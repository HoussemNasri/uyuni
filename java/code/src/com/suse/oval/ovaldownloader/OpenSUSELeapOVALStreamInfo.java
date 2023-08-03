package com.suse.oval.ovaldownloader;

class OpenSUSELeapOVALStreamInfo extends OVALStreamInfo {
    public final String osVersion;

    OpenSUSELeapOVALStreamInfo(String osVersion) {
        this.osVersion = osVersion;
    }

    @Override
    String localFileName() {
        return "opensuse.leap-" + osVersion;
    }

    @Override
    String remoteFileUrl() {
        return String.format("https://ftp.suse.com/pub/projects/security/oval/opensuse.leap.%s-affected.xml.gz", osVersion);
    }

    @Override
    OVALCompressionMethod getCompressionMethod() {
        return OVALCompressionMethod.GZIP;
    }
}
