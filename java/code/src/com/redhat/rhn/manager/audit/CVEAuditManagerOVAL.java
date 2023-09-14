/*
 * Copyright (c) 2023 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package com.redhat.rhn.manager.audit;


import static com.redhat.rhn.manager.audit.CVEAuditManager.SUCCESSOR_PRODUCT_RANK_BOUNDARY;

import com.redhat.rhn.domain.rhnpackage.PackageEvr;
import com.redhat.rhn.domain.server.Server;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.manager.rhnpackage.PackageManager;

import com.suse.oval.OVALCachingFactory;
import com.suse.oval.ShallowSystemPackage;

import com.suse.oval.OVALCleaner;
import com.suse.oval.OsFamily;
import com.suse.oval.OvalParser;
import com.suse.oval.config.OVALConfigLoader;
import com.suse.oval.ovaldownloader.OVALDownloadResult;
import com.suse.oval.ovaldownloader.OVALDownloader;
import com.suse.oval.ovaltypes.OvalRootType;
import com.suse.oval.vulnerablepkgextractor.VulnerablePackage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class, same as {@link CVEAuditManager}, provides the functionality of CVE auditing.It bases its evaluation on
 * OVAL data, in addition to channels data. Therefore, it provides more accurate results.
 * <p>
 * We can't get rid of {@link CVEAuditManager} yet because not all supported Linux distributions provide
 * OVAL vulnerability definitions, thus, we fall back to {@link CVEAuditManager} in that case.
 *
 */
public class CVEAuditManagerOVAL {

    private static final Logger LOG = LogManager.getLogger(CVEAuditManagerOVAL.class);

    private CVEAuditManagerOVAL() {

    }

    /**
     * List visible systems with their patch status regarding a given CVE identifier.
     *
     * @param user the calling user
     * @param cveIdentifier the CVE identifier to lookup
     * @param patchStatuses the patch statuses
     * @return list of system records with patch status
     * @throws UnknownCVEIdentifierException if the CVE number is not known
     */
    public static List<CVEAuditServer> listSystemsByPatchStatus(User user, String cveIdentifier,
                                                                EnumSet<PatchStatus> patchStatuses)
            throws UnknownCVEIdentifierException {
        if (isCVEIdentifierUnknown(cveIdentifier)) {
            throw new UnknownCVEIdentifierException();
        }

        List<CVEAuditServer> result = new ArrayList<>();

        List<CVEAuditManager.CVEPatchStatus> results = CVEAuditManager.listSystemsByPatchStatus(user, cveIdentifier)
                .collect(Collectors.toList());

        // Group the results by system
        Map<Long, List<CVEAuditManager.CVEPatchStatus>> resultsBySystem =
                results.stream().collect(Collectors.groupingBy(CVEAuditManager.CVEPatchStatus::getSystemId));

        Set<Server> clients = user.getServers();
        for (Server clientServer : clients) {
            CVEAuditSystemBuilder systemAuditResult;
            // We need this initially to be able to get errata and audit channels information for the OVAL
            // implementation.
            // TODO: We could make a custom query that would get us only the data we're interested in instead of relying
            //  on CVEAuditManager#doAuditSystem implementation.

            CVEAuditSystemBuilder auditWithChannelsResult =
                    CVEAuditManager.doAuditSystem(clientServer.getId(), resultsBySystem.get(clientServer.getId()));

            if (doesSupportOVALAuditing(clientServer)) {
                systemAuditResult = doAuditSystem(cveIdentifier, resultsBySystem.get(clientServer.getId()),
                        clientServer);
                systemAuditResult.setChannels(auditWithChannelsResult.getChannels());
                systemAuditResult.setErratas(auditWithChannelsResult.getErratas());
            }
            else {
                systemAuditResult = auditWithChannelsResult;
            }

            if (patchStatuses.contains(systemAuditResult.getPatchStatus())) {
                result.add(new CVEAuditServer(
                        systemAuditResult.getId(),
                        systemAuditResult.getSystemName(),
                        systemAuditResult.getPatchStatus(),
                        systemAuditResult.getChannels(),
                        systemAuditResult.getErratas()));
            }
        }

        return result;
    }

    private static boolean isCVEIdentifierUnknown(String cveIdentifier) {
        return !OVALCachingFactory.canAuditCVE(cveIdentifier);
    }

    /**
     * Check if server support OVAL CVE auditing
     *
     * @param clientServer the server to check
     * @return {@code True}
     * */
    public static boolean doesSupportOVALAuditing(Server clientServer) {
        // TODO: check if OVAL is synced and client product is support .e.g. Red Hat, Debian, Ubuntu or SUSE
        return true;
    }

    /**
     * Audit the given {@code clientServer} regarding the given CVE identifier based on OVAL and Channels data.
     *
     * @param clientServer the server to audit
     * @param results list produced by {@link CVEAuditManager#listSystemsByPatchStatus(User, String)},
     *                helpful for determining the availability of a patch for the vulnerability in channels.
     * @param cveIdentifier the CVE identifier
     * @return a record with data about a single system containing that system's patch status regarding a certain
     * given CVE identifier as well as sets of relevant channels and erratas.
     * */
    public static CVEAuditSystemBuilder doAuditSystem(String cveIdentifier,
                                                      List<CVEAuditManager.CVEPatchStatus> results,
                                                      Server clientServer) {
        // It's possible to have 2 or more patches for one package. It's necessary to apply all of them because
        // they will have the same outcome i.e. patch the package; instead we need to choose only one.
        // To choose the one, we rank patches based on the channel they come from .e.g.
        // assigned, successor product, etc. And for each vulnerable package we keep only the highest ranking patch
        results = keepOnlyPatchCandidates(results);

        CVEAuditSystemBuilder cveAuditServerBuilder = new CVEAuditSystemBuilder(clientServer.getId());
        cveAuditServerBuilder.setSystemName(clientServer.getName());

        List<ShallowSystemPackage> allInstalledPackages =
                PackageManager.shallowSystemPackageList(clientServer.getId());

        LOG.error("Vul packages before filtering: {}",
                OVALCachingFactory.getVulnerablePackagesByProductAndCve(clientServer.getCpe(), cveIdentifier));

        Set<VulnerablePackage> clientProductVulnerablePackages =
                OVALCachingFactory.getVulnerablePackagesByProductAndCve(clientServer.getCpe(), cveIdentifier).stream()
                        .filter(pkg -> isPackageInstalled(pkg, allInstalledPackages))
                        .collect(Collectors.toSet());

        LOG.error("Vul packages: {}", clientProductVulnerablePackages);

        if (clientProductVulnerablePackages.isEmpty()) {
            cveAuditServerBuilder.setPatchStatus(PatchStatus.NOT_AFFECTED);
            return cveAuditServerBuilder;
        }

        Set<VulnerablePackage> patchedVulnerablePackages = clientProductVulnerablePackages.stream()
                .filter(vulnerablePackage -> vulnerablePackage.getFixVersion().isPresent()).collect(
                        Collectors.toSet());

        Set<VulnerablePackage> unpatchedVulnerablePackages = clientProductVulnerablePackages.stream()
                .filter(vulnerablePackage -> vulnerablePackage.getFixVersion().isEmpty()).collect(
                        Collectors.toSet());

        if (patchedVulnerablePackages.isEmpty() && !unpatchedVulnerablePackages.isEmpty()) {
            cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_PATCH_UNAVAILABLE);
        }
        else {
            boolean allPackagesPatched = patchedVulnerablePackages.stream().allMatch(patchedPackage ->
                    allInstalledPackages.stream()
                            .filter(installedPackage ->
                                    Objects.equals(installedPackage.getName(), patchedPackage.getName()))
                            .anyMatch(installedPackage ->
                                    installedPackage.getPackageEVR()
                                            .compareTo(PackageEvr.parseRpm(
                                                    patchedPackage.getFixVersion().get())) >= 0));

            if (allPackagesPatched) {
                cveAuditServerBuilder.setPatchStatus(PatchStatus.PATCHED);
            }
            else {
                List<CVEAuditManager.CVEPatchStatus> patchesInAssignedChannels = results.stream()
                        .filter(CVEAuditManager.CVEPatchStatus::isChannelAssigned)
                        .collect(Collectors.toList());

                List<CVEAuditManager.CVEPatchStatus> patchesInUnassignedChannels = results.stream()
                        .filter(cvePatchStatus -> !cvePatchStatus.isChannelAssigned())
                        .collect(Collectors.toList());

                long numberOfPackagesWithPatchInAssignedChannels =
                        patchedVulnerablePackages.stream().filter(patchedPackage -> patchesInAssignedChannels
                                .stream()
                                .anyMatch(patch ->
                                        patch.getPackageName().equals(Optional.of(patchedPackage.getName()))
                                )
                        ).count();

                boolean allPackagesHavePatchInAssignedChannels =
                        numberOfPackagesWithPatchInAssignedChannels == patchedVulnerablePackages.size();
                boolean somePackagesHavePatchInAssignedChannels = numberOfPackagesWithPatchInAssignedChannels > 0;

                if (allPackagesHavePatchInAssignedChannels) {
                    cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_FULL_PATCH_APPLICABLE);
                }
                else if (somePackagesHavePatchInAssignedChannels) {
                    cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_PARTIAL_PATCH_APPLICABLE);
                }
                else {
                    long numberOfPackagesWithPatchInUnassignedChannels =
                            patchedVulnerablePackages.stream().filter(patchedPackage -> patchesInUnassignedChannels
                                    .stream()
                                    .anyMatch(patch ->
                                            patch.getPackageName().equals(Optional.of(patchedPackage.getName()))
                                    )
                            ).count();

                    boolean somePackagesHavePatchInUnassignedChannels =
                            numberOfPackagesWithPatchInUnassignedChannels > 0 &&
                                    numberOfPackagesWithPatchInUnassignedChannels == patchedVulnerablePackages.size();

                    boolean allPackagesHavePatchInUnassignedChannels =
                            numberOfPackagesWithPatchInUnassignedChannels == patchedVulnerablePackages.size();

                    if (allPackagesHavePatchInUnassignedChannels) {
                        boolean allPackagesHavePatchInSuccessorChannel = patchesInUnassignedChannels.stream()
                                .allMatch(patch ->
                                        patch.getChannelRank().orElse(0L) >= SUCCESSOR_PRODUCT_RANK_BOUNDARY);
                        if (allPackagesHavePatchInSuccessorChannel) {
                            cveAuditServerBuilder
                                    .setPatchStatus(PatchStatus.AFFECTED_PATCH_INAPPLICABLE_SUCCESSOR_PRODUCT);
                        }
                        else {
                            cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_PATCH_INAPPLICABLE);
                        }
                    }
                    else if (somePackagesHavePatchInUnassignedChannels) {
                        //TODO: Not sure how to handle...
                        cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_PATCH_UNAVAILABLE);
                    }
                    else {
                        cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_PATCH_UNAVAILABLE);
                    }
                }
            }
        }

        LOG.error("Patch Status: {}", cveAuditServerBuilder.getPatchStatus());

        return cveAuditServerBuilder;
    }

    private static List<CVEAuditManager.CVEPatchStatus> keepOnlyPatchCandidates(
            List<CVEAuditManager.CVEPatchStatus> results) {
        List<CVEAuditManager.CVEPatchStatus> patchCandidates = new ArrayList<>();

        Map<String, List<CVEAuditManager.CVEPatchStatus>> resultsByPackage = results.stream()
                .filter(result -> result.getPackageName().isPresent())
                .collect(Collectors.groupingBy(r -> r.getPackageName().get()));

        for (String packageName : resultsByPackage.keySet()) {
            List<CVEAuditManager.CVEPatchStatus> packageResults = resultsByPackage.get(packageName);
            CVEAuditManager.getPatchCandidateResult(packageResults).ifPresent(patchCandidates::add);
        }

        return patchCandidates;
    }

    private static boolean isPackageInstalled(VulnerablePackage pkg, List<ShallowSystemPackage> allInstalledPackages) {
        return allInstalledPackages.stream()
                .anyMatch(installed -> Objects.equals(installed.getName(), pkg.getName()));
    }

    /**
     * List visible images with their patch status regarding a given CVE identifier.
     *
     * @param user the calling user
     * @param cveIdentifier the CVE identifier to lookup
     * @param patchStatuses the patch statuses
     * @return list of images records with patch status
     * @throws UnknownCVEIdentifierException if the CVE number is not known
     */
    public static List<CVEAuditImage> listImagesByPatchStatus(User user,
                                                              String cveIdentifier, EnumSet<PatchStatus> patchStatuses)
            throws UnknownCVEIdentifierException {
        // TODO: Audit images with OVAL
        return CVEAuditManager.listImagesByPatchStatus(user, cveIdentifier, patchStatuses);
    }

    /**
     * Populate channels for CVE Audit
     * */
    public static void populateCVEChannels() {
        CVEAuditManager.populateCVEChannels();
    }

    static List<OVALProduct> productsToSync = new ArrayList<>();
    static {
        /*productsToSync.add(new OVALProduct(OsFamily.openSUSE_LEAP, "15.4"));*/
        productsToSync.add(new OVALProduct(OsFamily.openSUSE_LEAP, "15.3"));
        productsToSync.add(new OVALProduct(OsFamily.REDHAT_ENTERPRISE_LINUX, "9"));
    }
    public static void syncOVAL() {
        OVALDownloader ovalDownloader = new OVALDownloader(OVALConfigLoader.load());
        for (OVALProduct product : productsToSync) {
            LOG.warn("Downloading OVAL for {} {}", product.getOsFamily(), product.getOsVersion());
            OVALDownloadResult downloadResult;
            try {
                downloadResult = ovalDownloader.download(product.getOsFamily(), product.getOsVersion());
            }
            catch (IOException e) {
                throw new RuntimeException("Failed to download OVAL data", e);
            }
            LOG.warn("Downloading finished");

            LOG.warn("OVAL vulnerability file: " +
                    downloadResult.getVulnerabilityFile().map(File::getAbsoluteFile).orElse(null));
            LOG.warn("OVAL patch file: " + downloadResult.getPatchFile().map(File::getAbsoluteFile).orElse(null));

            downloadResult.getVulnerabilityFile().ifPresent(ovalVulnerabilityFile -> {
                OvalParser ovalParser = new OvalParser();
                OvalRootType ovalRoot = ovalParser.parse(ovalVulnerabilityFile);

                LOG.warn("Saving Vulnerability OVAL for {} {}", product.getOsFamily(), product.getOsVersion());

                OVALCleaner.cleanup(ovalRoot, product.getOsFamily(), product.getOsVersion());
                OVALCachingFactory.savePlatformsVulnerablePackages(ovalRoot);
            });

            downloadResult.getPatchFile().ifPresent(patchFile -> {
                OvalParser ovalParser = new OvalParser();
                OvalRootType ovalRoot = ovalParser.parse(patchFile);

                LOG.warn("Saving Patch OVAL for {} {}", product.getOsFamily(), product.getOsVersion());

                OVALCleaner.cleanup(ovalRoot, product.getOsFamily(), product.getOsVersion());
                OVALCachingFactory.savePlatformsVulnerablePackages(ovalRoot);
            });

            LOG.warn("Saving OVAL finished");
        }
    }

    public static class OVALProduct {
        private OsFamily osFamily;
        private String osVersion;

        public OVALProduct(OsFamily osFamily, String osVersion) {
            this.osFamily = osFamily;
            this.osVersion = osVersion;
        }

        public OsFamily getOsFamily() {
            return osFamily;
        }

        public void setOsFamily(OsFamily osFamily) {
            this.osFamily = osFamily;
        }

        public String getOsVersion() {
            return osVersion;
        }

        public void setOsVersion(String osVersion) {
            this.osVersion = osVersion;
        }
    }
}
