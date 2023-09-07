package com.suse.oval;

import com.redhat.rhn.common.hibernate.HibernateFactory;
import com.redhat.rhn.domain.errata.Cve;
import com.redhat.rhn.domain.errata.CveFactory;
import com.suse.oval.db.*;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OVALCachingFactory extends HibernateFactory {
    private static final Logger LOG = LogManager.getLogger(OVALCachingFactory.class);
    private static OVALCachingFactory instance = new OVALCachingFactory();

    private OVALCachingFactory() {
        // Left empty on purpose
    }

    /**
     * Insert the passed definition object into the database.
     * <p>
     * Also inserts the affected platforms information (if not already inserted) into the relevant tables
     */
    public static void saveDefinition(OVALDefinition definition, List<String> affectedPlatforms) {
        definition.setAffectedPlatforms(
                affectedPlatforms.stream()
                        .map(OVALCachingFactory::lookupOrInsertPlatformByName)
                        .collect(Collectors.toList())
        );

        // TODO: OVAL source should be included in the OVALDefinition object
        String cve = extractCveFromDefinition(definition, OVALDefinitionSource.SUSE);
        definition.setCve(CveFactory.lookupOrInsertByName(cve));

        instance.saveObject(definition);
    }

    /**
     * Looks up an {@link OVALPlatform} or inserts it if it does not exist.
     *
     * @param name name of the platform
     * @return the platform
     */
    public static OVALPlatform lookupOrInsertPlatformByName(String name) {
        OVALPlatform platform = lookupPlatformByName(name);
        if (platform != null) {
            return platform;
        } else {
            OVALPlatform newPlatform = new OVALPlatform();
            newPlatform.setName(name);
            instance.saveObject(newPlatform);

            return newPlatform;
        }
    }

    public static OVALPlatform lookupPlatformByName(String name) {
        Session session = HibernateFactory.getSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();

        CriteriaQuery<OVALPlatform> criteriaQuery = builder.createQuery(OVALPlatform.class);
        Root<OVALPlatform> root = criteriaQuery.from(OVALPlatform.class);

        criteriaQuery.where(builder.equal(root.get("name"), name));

        return session.createQuery(criteriaQuery).uniqueResult();
    }

    public static void savePackageTest(OVALPackageTest pkgTest) {

    }

    public static void savePackageState(OVALPackageState pkgState) {

    }

    public static void savePackageObject(OVALPackageObject pkgObject) {

    }

    public static OVALPackageTest lookupPackageTestById(String id) {
        return getSession().byId(OVALPackageTest.class).load(id);
    }

    public static OVALPackageState lookupPackageStateById(String id) {
        return getSession().byId(OVALPackageState.class).load(id);
    }

    public static OVALPackageObject lookupPackageObjectById(String id) {
        return getSession().byId(OVALPackageObject.class).load(id);
    }

    public static List<OVALPlatform> getPlatformsAffectedByCve(String cve) {
        return getSession()
                .createNamedQuery("OVALPlatform.getPlatformsAffectedByCve", OVALPlatform.class)
                .setParameter("cve", cve)
                .getResultList();
    }

    public static List<OVALPlatform> lookupPlatformsAffectedByDefinition(String defId) {
        return getSession()
                .createNamedQuery("OVALPlatform.getPlatformsAffectedByDefinition", OVALPlatform.class)
                .setParameter("defId", defId)
                .getResultList();
    }

    public static Optional<OVALDefinition> lookupVulnerabilityDefinitionByCve(String cve) {
        return getSession()
                .createNamedQuery("OVALDefinition.getVulnerabilityDefinitionByCve", OVALDefinition.class)
                .setParameter("cve", cve)
                .uniqueResultOptional();
    }

    public static List<OVALVulnerablePackage> lookupVulnerablePackagesByPlatformAndCve(long platformId, String cve) {
        return Collections.emptyList();
    }

    public static void assignVulnerablePackageToPlatform(String platformName, String cveName, String pkgName, String pkgFixVersion) {
        OVALPlatform platform = lookupPlatformByName(platformName);
        Cve cve = CveFactory.lookupByName(cveName);
        OVALVulnerablePackage vulnerablePkg = lookupOrInsertVulnerablePackage(pkgName, pkgFixVersion);

        OVALPlatformVulnerablePackage platformVulnerablePkg = new OVALPlatformVulnerablePackage();
        platformVulnerablePkg.setPlatform(platform);
        platformVulnerablePkg.setCve(cve);
        platformVulnerablePkg.setVulnerablePackage(vulnerablePkg);

        instance.saveObject(platformVulnerablePkg);
    }

    public static OVALVulnerablePackage lookupOrInsertVulnerablePackage(String pkgName, String fixVersion) {
        OVALVulnerablePackage vulnerablePackage = lookupVulnerablePackageByNameAndFixVersion(pkgName, fixVersion);
        if (vulnerablePackage != null) {
            return vulnerablePackage;
        } else {
            OVALVulnerablePackage newVulnerablePackage = new OVALVulnerablePackage();
            newVulnerablePackage.setName(pkgName);
            newVulnerablePackage.setFixVersion(fixVersion);

            instance.saveObject(newVulnerablePackage);

            return newVulnerablePackage;
        }
    }

    public static OVALVulnerablePackage lookupVulnerablePackageByNameAndFixVersion(String pkgName, String fixVersion) {
        Session session = HibernateFactory.getSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();

        CriteriaQuery<OVALVulnerablePackage> criteriaQuery = builder.createQuery(OVALVulnerablePackage.class);
        Root<OVALVulnerablePackage> root = criteriaQuery.from(OVALVulnerablePackage.class);

        criteriaQuery.where(builder.and(
                        builder.equal(root.get("name"), pkgName),
                        builder.equal(root.get("fix_version"), fixVersion)
                )
        );

        return session.createQuery(criteriaQuery).uniqueResult();
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    private static String extractCveFromDefinition(OVALDefinition definition, OVALDefinitionSource source) {
        switch (source) {
            case SUSE:
                return definition.getTitle();
            case DEBIAN:
            case REDHAT:
            case UBUNTU:
                throw new NotImplementedException("Cannot extract cve from '" + source + "' OVAL definitions");
        }

        return "";
    }
}
