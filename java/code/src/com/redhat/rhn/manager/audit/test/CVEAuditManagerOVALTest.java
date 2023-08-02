package com.redhat.rhn.manager.audit.test;

import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.errata.Cve;
import com.redhat.rhn.domain.errata.Errata;
import com.redhat.rhn.domain.rhnpackage.Package;
import com.redhat.rhn.domain.server.Server;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.manager.audit.CVEAuditManager;
import com.redhat.rhn.manager.audit.CVEAuditManagerOVAL;
import com.redhat.rhn.manager.audit.CVEAuditSystemBuilder;
import com.redhat.rhn.manager.audit.PatchStatus;
import com.redhat.rhn.testing.RhnBaseTestCase;
import com.redhat.rhn.testing.TestUtils;
import com.suse.oval.OvalParser;
import com.suse.oval.ovaltypes.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.redhat.rhn.domain.rhnpackage.test.PackageNameTest.createTestPackageName;
import static com.redhat.rhn.testing.ErrataTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CVEAuditManagerOVALTest extends RhnBaseTestCase {
    private static final Logger log = LogManager.getLogger(CVEAuditManagerOVALTest.class);

    OvalParser ovalParser = new OvalParser();

    @Test
    void testDoAuditSystemNotAffected() throws Exception {
        OvalRootType ovalRoot = ovalParser.parse(TestUtils
                .findTestData("/com/redhat/rhn/manager/audit/test/oval/oval-def-1.xml"));

        DefinitionType definitionType = ovalRoot.getDefinitions().get(0);

        saveAllOVALTests(ovalRoot);

        Cve cve = createTestCve("CVE-2022-2991");

        Set<Cve> cves = Set.of(cve);
        User user = createTestUser();

        Errata errata = createTestErrata(user, cves);
        Channel channel = createTestChannel(user, errata);
        Set<Channel> channels = Set.of(channel);

        Server server = createTestServer(user, channels);
        server.setCpe("cpe:/o:opensuse:leap:15.5"); // Not Leap 15.4

        createOVALDefinition(definitionType);

        CVEAuditManager.populateCVEChannels();

        List<CVEAuditManager.CVEPatchStatus> results = CVEAuditManager.listSystemsByPatchStatus(user, cve.getName())
                .collect(Collectors.toList());

        CVEAuditSystemBuilder systemAuditResult = CVEAuditManagerOVAL.doAuditSystem(cve.getName(), results, server);

        assertEquals(PatchStatus.NOT_AFFECTED, systemAuditResult.getPatchStatus());
    }

    @Test
    void testDoAuditSystemPatched() throws Exception {
        OvalRootType ovalRoot = ovalParser.parse(TestUtils
                .findTestData("/com/redhat/rhn/manager/audit/test/oval/oval-def-1.xml"));

        // TODO: Compute object hash to make sure we update tests whenever test OVAL files changes
        DefinitionType definitionType = ovalRoot.getDefinitions().get(0);

        saveAllOVALTests(ovalRoot);

        Cve cve = createTestCve("CVE-2022-2991");

        Set<Cve> cves = Set.of(cve);
        User user = createTestUser();

        Errata errata = createTestErrata(user, cves);
        Channel channel = createTestChannel(user, errata);
        Set<Channel> channels = Set.of(channel);

        Server server = createTestServer(user, channels);
        server.setCpe("cpe:/o:opensuse:leap:15.4");

        createOVALDefinition(definitionType);

        Package unpatched = createTestPackage(user, channel, "noarch");
        unpatched.setPackageName(createTestPackageName("kernel-debug-base"));
        Package patched = createLaterTestPackage(user, errata, channel, unpatched,
                "0", "4.12.14", "150100.197.137.2");

        createTestInstalledPackage(createLeap15_4_Package(user, errata, channel), server);
        createTestInstalledPackage(patched, server);

        CVEAuditManager.populateCVEChannels();

        List<CVEAuditManager.CVEPatchStatus> results = CVEAuditManager.listSystemsByPatchStatus(user, cve.getName())
                .collect(Collectors.toList());

        CVEAuditSystemBuilder systemAuditResult = CVEAuditManagerOVAL.doAuditSystem(cve.getName(), results, server);

        assertEquals(PatchStatus.PATCHED, systemAuditResult.getPatchStatus());
    }

    @Test
    void testDoAuditSystemAffectedFullPatchAvailable() throws Exception {
        OvalRootType ovalRoot = ovalParser.parse(TestUtils
                .findTestData("/com/redhat/rhn/manager/audit/test/oval/oval-def-1.xml"));

        // TODO: Compute object hash to make sure we update tests whenever test OVAL files changes
        DefinitionType definitionType = ovalRoot.getDefinitions().get(0);

        saveAllOVALTests(ovalRoot);

        Cve cve = createTestCve("CVE-2022-2991");

        Set<Cve> cves = Set.of(cve);
        User user = createTestUser();

        Errata errata = createTestErrata(user, cves);
        Channel channel = createTestChannel(user, errata);
        Set<Channel> channels = Set.of(channel);

        Server server = createTestServer(user, channels);
        server.setCpe("cpe:/o:opensuse:leap:15.4");

        createOVALDefinition(definitionType);

        Package unpatched = createTestPackage(user, channel, "noarch",
                "kernel-debug-base", "0", "4.12.13", "150100.197.137.2");

        Package patched = createTestPackage(user, errata, channel, "noarch",
                "kernel-debug-base", "0", "4.12.14", "150100.197.137.2");

        log.error(unpatched.getPackageEvr().toUniversalEvrString());

        createTestInstalledPackage(createLeap15_4_Package(user, errata, channel), server);
        createTestInstalledPackage(unpatched, server);

        server.getPackages().forEach(p -> log.error(p.getName().getName() + "--" + p.getEvr().toUniversalEvrString()));

        CVEAuditManager.populateCVEChannels();

        server.getPackages().forEach(p -> log.error(p.getName().getName() + "--" + p.getEvr().toUniversalEvrString()));

        List<CVEAuditManager.CVEPatchStatus> results = CVEAuditManager.listSystemsByPatchStatus(user, cve.getName())
                .collect(Collectors.toList());

        log.error(server.getName());
        results.forEach(r -> log.error(r.getPackageName() + ":" + r.getPackageEvr() + ":" + r.isPackageInstalled() + ":" + r.getSystemName()));

        CVEAuditSystemBuilder systemAuditResult = CVEAuditManagerOVAL.doAuditSystem(cve.getName(), results, server);

        assertEquals(PatchStatus.AFFECTED_FULL_PATCH_APPLICABLE, systemAuditResult.getPatchStatus());
    }


    @Test
    void testDoAuditSystemAffectedPatchUnavailable() throws Exception {
        OvalRootType ovalRoot = ovalParser.parse(TestUtils
                .findTestData("/com/redhat/rhn/manager/audit/test/oval/oval-def-2.xml"));

        DefinitionType definitionType = ovalRoot.getDefinitions().get(0);

        saveAllOVALTests(ovalRoot);

        Cve cve = createTestCve("CVE-2008-2934");

        Set<Cve> cves = Set.of(cve);
        User user = createTestUser();

        Errata errata = createTestErrata(user, cves);
        Channel channel = createTestChannel(user, errata);
        Set<Channel> channels = Set.of(channel);

        Server server = createTestServer(user, channels);
        server.setCpe("cpe:/o:opensuse:leap:15.4");

        createOVALDefinition(definitionType);

        Package affected =  createTestPackage(user, channel, "noarch", "MozillaFirefox");
        createTestPackage(user, channel, "noarch", "MozillaFirefox-devel");

        createTestInstalledPackage(createLeap15_4_Package(user, errata, channel), server);
        createTestInstalledPackage(affected, server);

        CVEAuditManager.populateCVEChannels();

        List<CVEAuditManager.CVEPatchStatus> results = CVEAuditManager.listSystemsByPatchStatus(user, cve.getName())
                .collect(Collectors.toList());

        results.forEach(r -> log.error(r.getPackageName() + ":" + r.getPackageEvr() + ":" + r.isPackageInstalled() + ":" + r.getSystemName()));

        CVEAuditSystemBuilder systemAuditResult = CVEAuditManagerOVAL.doAuditSystem(cve.getName(), results, server);

        assertEquals(PatchStatus.AFFECTED_PATCH_UNAVAILABLE, systemAuditResult.getPatchStatus());
    }

    @Test
    void testDoAuditSystemAffectedPartialPatchAvailable_FalsePositive() throws Exception {
        OvalRootType ovalRoot = ovalParser.parse(TestUtils
                .findTestData("/com/redhat/rhn/manager/audit/test/oval/oval-def-3.xml"));

        DefinitionType definitionType = ovalRoot.getDefinitions().get(0);

        saveAllOVALTests(ovalRoot);

        Cve cve = createTestCve("CVE-2008-2934");

        Set<Cve> cves = Set.of(cve);
        User user = createTestUser();

        Errata errata = createTestErrata(user, cves);
        Channel channel = createTestChannel(user, errata);
        Set<Channel> channels = Set.of(channel);

        Server server = createTestServer(user, channels);
        server.setCpe("cpe:/o:opensuse:leap:15.4");

        createOVALDefinition(definitionType);

        createTestPackage(user, errata, channel, "noarch", "MozillaFirefox", "0", "2.4.0", "150400.1.12");
        Package unpatched =  createTestPackage(user, channel, "noarch", "MozillaFirefox", "0", "2.3.0", "150400.1.12");

        // The 'MozillaFirefox-devel' package is vulnerable and should be patched according to the OVAL data,
        // but is not installed on the system.
        // Therefore, even though 'MozillaFirefox-devel' doesn't have a patch in the assigned channels,
        // the algorithm should return AFFECTED_FULL_PATCH_APPLICABLE(because 'MozillaFirefox' has a patch)
        // instead of AFFECTED_PARTIAL_PATCH_APPLICABLE
        createTestPackage(user, channel, "noarch", "MozillaFirefox-devel");

        createTestInstalledPackage(createLeap15_4_Package(user, errata, channel), server);
        createTestInstalledPackage(unpatched, server);

        CVEAuditManager.populateCVEChannels();

        List<CVEAuditManager.CVEPatchStatus> results = CVEAuditManager.listSystemsByPatchStatus(user, cve.getName())
                .collect(Collectors.toList());

        results.forEach(r -> log.error(r.getPackageName() + ":" + r.getPackageEvr() + ":" + r.isPackageInstalled() + ":" + r.getSystemName()));

        CVEAuditSystemBuilder systemAuditResult = CVEAuditManagerOVAL.doAuditSystem(cve.getName(), results, server);

        assertEquals(PatchStatus.AFFECTED_FULL_PATCH_APPLICABLE, systemAuditResult.getPatchStatus());
    }

    /**
     * This package is used to distinguish openSUSE Leap 15.4 distributions. We use very often in tests, so
     * it's abstracted here
     * */
    private static Package createLeap15_4_Package(User user, Errata errata, Channel channel) throws Exception {
        return createTestPackage(user, channel, "noarch", "openSUSE-release",
                "0", "15.4", "0");
    }

    /**
     * Using this method requires that each triple of OVAL (test, object, state) be located at the same position in
     * the OVAL file under their own category.
     * */
    private static void saveAllOVALTests(OvalRootType ovalRoot) {
        for (int i = 0; i < ovalRoot.getTests().getTests().size(); i++) {
            TestType testType = ovalRoot.getTests().getTests().get(i);
            ObjectType objectType = ovalRoot.getObjects().getObjects().get(i);
            StateType stateType = ovalRoot.getStates().getStates().get(i);

            createOVALTest(testType, objectType, stateType);
        }
    }
}
