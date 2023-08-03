package com.suse.oval.ovaldownloader;

public class OpenSUSEOVALStreamInfo extends OVALStreamInfo {
    public final String osVersion;

    public OpenSUSEOVALStreamInfo(String osVersion) {
        this.osVersion = osVersion;
    }

    @Override
    String localFileName() {
        return "opensuse-" + osVersion;
    }

    @Override
    String remoteFileUrl() {
        return String.format("https://ftp.suse.com/pub/projects/security/oval/opensuse.%s.xml.gz", osVersion);
    }

    @Override
    OVALCompressionMethod getCompressionMethod() {
        return OVALCompressionMethod.GZIP;
    }
}
