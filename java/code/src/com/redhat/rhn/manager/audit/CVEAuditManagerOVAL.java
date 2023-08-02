package com.redhat.rhn.manager.audit;


import com.redhat.rhn.domain.server.Server;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.manager.rhnpackage.PackageManager;
import com.suse.oval.OVALCachingFactory;
import com.suse.oval.SystemPackage;
import com.suse.oval.db.OVALDefinition;
import com.suse.oval.vulnerablepkgextractor.VulnerablePackage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static com.redhat.rhn.manager.audit.CVEAuditManager.isCVEIdentifierUnknown;

/**
 * The OVAL version of the {@link CVEAuditManager}. This what should be used the other implementation is legacy.
 */
public class CVEAuditManagerOVAL {
    private static Logger log = LogManager.getLogger(CVEAuditManagerOVAL.class);

    public static List<CVEAuditServer> listSystemsByPatchStatus(User user, String cveIdentifier,
                                                                EnumSet<PatchStatus> patchStatuses) throws UnknownCVEIdentifierException {
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
            //  on CVEAuditManager#doAuditManager implementation.
            CVEAuditSystemBuilder auditWithChannelsResult = CVEAuditManager.doAuditSystem(clientServer.getId(), results);

            if (doesSupportOVALAuditing(clientServer)) {
                systemAuditResult = doAuditSystem(cveIdentifier, resultsBySystem.get(clientServer.getId()), clientServer);

                systemAuditResult.setChannels(auditWithChannelsResult.getChannels());
                systemAuditResult.setErratas(auditWithChannelsResult.getErratas());
            } else {
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

    public static boolean doesSupportOVALAuditing(Server clientServer) {
        // TODO: check if OVAL is synced and client product is Red Hat, Debian, Ubuntu or SUSE
        return true;
    }

    public static CVEAuditSystemBuilder doAuditSystem(String cveIdentifier, List<CVEAuditManager.CVEPatchStatus> results,
                                                      Server clientServer) {

        Optional<OVALDefinition> vulnerabilityDefinitionOpt = OVALCachingFactory
                .lookupVulnerabilityDefinitionByCve(cveIdentifier);

        if (vulnerabilityDefinitionOpt.isEmpty()) {
            log.warn("The provided CVE does not match any OVAL definition in the database.");
        }

        OVALDefinition vulnerabilityDefinition = vulnerabilityDefinitionOpt.get();
        if (vulnerabilityDefinition.getCriteriaTree() == null) {
            log.warn("Vulnerability criteria tree for '{}' is unavailable", cveIdentifier);
        }

        CVEAuditSystemBuilder cveAuditServerBuilder = new CVEAuditSystemBuilder(clientServer.getId());
        cveAuditServerBuilder.setSystemName(clientServer.getName());

        Set<VulnerablePackage> clientProductVulnerablePackages =
                vulnerabilityDefinition.extractVulnerablePackages(clientServer.getCpe());

        List<SystemPackage> allInstalledPackages =
                PackageManager.systemPackageList(clientServer.getId());

        if (clientProductVulnerablePackages.isEmpty()) {
            cveAuditServerBuilder.setPatchStatus(PatchStatus.NOT_AFFECTED);
            return cveAuditServerBuilder;
        }

        boolean isClientServerVulnerable = vulnerabilityDefinition.evaluate(clientServer, allInstalledPackages);

        log.error("Evaluation: {}", isClientServerVulnerable);

        if (!isClientServerVulnerable) {
            cveAuditServerBuilder.setPatchStatus(PatchStatus.PATCHED);
            return cveAuditServerBuilder;
        }

        List<CVEAuditManager.CVEPatchStatus> systemResults = results.stream()
                .filter(CVEAuditManager.CVEPatchStatus::isChannelAssigned)
                .collect(Collectors.toList());

        int listSizeBefore = clientProductVulnerablePackages.size();

        clientProductVulnerablePackages.removeIf(
                p -> systemResults.stream()
                        .anyMatch(cps -> Objects.equals(cps.getPackageName().orElse(null), p.getName()))
        );

        log.error(clientProductVulnerablePackages);

        int listSizeAfter = clientProductVulnerablePackages.size();

        if (listSizeBefore > 0 && listSizeAfter == 0) {
            cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_FULL_PATCH_APPLICABLE);
        } else if (listSizeAfter < listSizeBefore) {
            cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_PARTIAL_PATCH_APPLICABLE);
        } else {
            boolean allPackagesAffected = clientProductVulnerablePackages
                    .stream()
                    .allMatch(p -> p.getFixVersion().isEmpty());

            if (allPackagesAffected) {
                cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_PATCH_UNAVAILABLE);
            } else {
                cveAuditServerBuilder.setPatchStatus(PatchStatus.AFFECTED_PATCH_INAPPLICABLE);
            }
        }

        log.error("Patch Status: {}", cveAuditServerBuilder.getPatchStatus());

        return cveAuditServerBuilder;
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
        return CVEAuditManager.listImagesByPatchStatus(user, cveIdentifier, patchStatuses);
    }

    public static List<RankedChannel> populateCVEChannels(AuditTarget auditTarget) {
        return CVEAuditManager.populateCVEChannels(auditTarget);
    }

    public static void populateCVEChannels() {
        CVEAuditManager.populateCVEChannels();
    }
}
