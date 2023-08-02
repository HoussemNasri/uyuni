package com.suse.oval.ovaltypes;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


@XmlType(name = "FamilyEnumeration", namespace = "http://oval.mitre.org/XMLSchema/oval-common-5")
@XmlEnum
public enum FamilyEnum {


    /**
     * The catos value describes the Cisco CatOS operating system.
     */
    @XmlEnumValue("catos")
    CATOS("catos"),

    /**
     * The ios value describes the Cisco IOS operating system.
     */
    @XmlEnumValue("ios")
    IOS("ios"),

    /**
     * The macos value describes the Mac operating system.
     */
    @XmlEnumValue("macos")
    MACOS("macos"),

    /**
     * The pixos value describes the Cisco PIX operating system.
     */
    @XmlEnumValue("pixos")
    PIXOS("pixos"),

    /**
     * The undefined value is to be used when the desired family is not available.
     */
    @XmlEnumValue("undefined")
    UNDEFINED("undefined"),

    /**
     * The unix value describes the UNIX operating system.
     */
    @XmlEnumValue("unix")
    UNIX("unix"),

    /**
     * The vmware_infrastructure value describes VMWare Infrastructure.
     */
    @XmlEnumValue("vmware_infrastructure")
    VMWARE_INFRASTRUCTURE("vmware_infrastructure"),

    /**
     * The windows value describes the Microsoft Windows operating system.
     */
    @XmlEnumValue("windows")
    WINDOWS("windows");
    private final String value;

    FamilyEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static FamilyEnum fromValue(String v) {
        for (FamilyEnum c: FamilyEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
