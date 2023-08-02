package com.suse.oval.db;

import com.redhat.rhn.domain.errata.Cve;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@Table(name = "suseOVALPlatformVulnerablePackage")
@IdClass(OVALPlatformVulnerablePackageKey.class)
@NamedQueries({
        @NamedQuery(name = "OVALVulnerablePackage.lookupVulnerablePackagesByPlatformAndCve",
                query = "SELECT vulnerablePkg " +
                        "FROM OVALPlatformVulnerablePackage platformVulnerablePkg " +
                        "JOIN platformVulnerablePkg.vulnerablePackage vulnerablePkg " +
                        "JOIN platformVulnerablePkg.cve cve " +
                        "JOIN platformVulnerablePkg.platform platform " +
                        "WHERE platform.id = :platformId AND cve.name = :cve")}
)
public class OVALPlatformVulnerablePackage {
    private OVALPlatform platform;
    private Cve cve;
    private OVALVulnerablePackage vulnerablePackage;

    @Id
    @ManyToOne
    @JoinColumn(name = "platform_id")
    public OVALPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(OVALPlatform platform) {
        this.platform = platform;
    }

    @SuppressWarnings("JpaAttributeTypeInspection")
    @Id
    @ManyToOne
    @JoinColumn(name = "cve_id")
    public Cve getCve() {
        return cve;
    }

    public void setCve(Cve cve) {
        this.cve = cve;
    }

    @Id
    @ManyToOne
    @JoinColumn(name = "vulnerable_pkg_id")
    public OVALVulnerablePackage getVulnerablePackage() {
        return vulnerablePackage;
    }

    public void setVulnerablePackage(OVALVulnerablePackage vulnerablePackage) {
        this.vulnerablePackage = vulnerablePackage;
    }
}
