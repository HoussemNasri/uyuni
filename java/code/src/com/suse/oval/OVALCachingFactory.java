package com.suse.oval;

import com.redhat.rhn.common.db.datasource.CallableMode;
import com.redhat.rhn.common.db.datasource.DataResult;
import com.redhat.rhn.common.db.datasource.ModeFactory;
import com.redhat.rhn.common.db.datasource.Row;
import com.redhat.rhn.common.db.datasource.SelectMode;
import com.redhat.rhn.common.hibernate.HibernateFactory;

import com.suse.oval.manager.OVALLookupHelper;
import com.suse.oval.ovaltypes.DefinitionType;
import com.suse.oval.ovaltypes.OvalRootType;
import com.suse.oval.vulnerablepkgextractor.ProductVulnerablePackages;
import com.suse.oval.vulnerablepkgextractor.VulnerablePackage;
import com.suse.oval.vulnerablepkgextractor.VulnerablePackagesExtractor;
import com.suse.oval.vulnerablepkgextractor.VulnerablePackagesExtractors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class OVALCachingFactory extends HibernateFactory {
    private static final Logger LOG = LogManager.getLogger(OVALCachingFactory.class);
    public static final int BATCH_SIZE = 60;
    private static OVALCachingFactory instance = new OVALCachingFactory();

    private OVALCachingFactory() {
        // Left empty on purpose
    }

    public static void savePlatformsVulnerablePackages(OvalRootType rootType) {
        CallableMode mode = ModeFactory.getCallableMode("oval_queries", "add_product_vulnerable_package");

        OVALLookupHelper ovalLookupHelper = new OVALLookupHelper(rootType);

        DataResult<Map<String, Object>> batch = new DataResult<>(new ArrayList<>(1000));

        for (DefinitionType definition : rootType.getDefinitions()) {
            VulnerablePackagesExtractor vulnerablePackagesExtractor =
                    VulnerablePackagesExtractors.create(definition, rootType.getOsFamily(), ovalLookupHelper);

            List<ProductVulnerablePackages> extractionResult = vulnerablePackagesExtractor.extract();
            for (ProductVulnerablePackages productVulnerablePackages : extractionResult) {
                for (String cve : productVulnerablePackages.getCves()) {
                    for (VulnerablePackage vulnerablePackage : productVulnerablePackages.getVulnerablePackages()) {
                        Map<String, Object> params = new HashMap<>();
                        params.put("product_name", productVulnerablePackages.getProductCpe());
                        params.put("cve_name", cve);
                        params.put("package_name", vulnerablePackage.getName());
                        params.put("fix_version", vulnerablePackage.getFixVersion().orElse(null));

                        batch.add(params);

                        if (batch.size() % 1000 == 0) {
                            mode.getQuery().executeBatchUpdates(batch);
                            batch.clear();
                            commitTransaction();

                            Session session = getSession();
                            if (!inTransaction()) {
                                session.beginTransaction();
                            }
                        }
                    }
                }
            }
        }

        mode.getQuery().executeBatchUpdates(new DataResult<>(batch));

        LOG.warn("Ending...");
    }

    public static List<VulnerablePackage> getVulnerablePackagesByProductAndCve(String productCpe, String cve) {
        SelectMode mode = ModeFactory.getMode("oval_queries", "get_vulnerable_packages");

        Map<String, Object> params = new HashMap<>();
        params.put("cve_name", cve);
        params.put("product_cpe", productCpe);

        DataResult<Row> result = mode.execute(params);

        return result.stream().map(row -> {
            VulnerablePackage vulnerablePackage = new VulnerablePackage();
            vulnerablePackage.setName((String) row.get("vulnerablepkgname"));
            vulnerablePackage.setFixVersion((String) row.get("vulnerablepkgfixversion"));
            return vulnerablePackage;
        }).collect(Collectors.toList());
    }

    private static <T> Stream<List<T>> toBatches(List<T> source) {
        int size = source.size();
        if (size == 0)
            return Stream.empty();
        int fullChunks = (size - 1) / BATCH_SIZE;
        return IntStream.range(0, fullChunks + 1).mapToObj(
                n -> source.subList(n * 60, n == fullChunks ? size : (n + 1) * 60));
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
