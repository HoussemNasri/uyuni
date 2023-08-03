package com.suse.oval.ovaldownloader;

public abstract class OVALStreamInfo {
    private static final String SUSE_URL = "https://ftp.suse.com/pub/projects/security/oval/";

    abstract String localFileName();

    abstract String remoteFileUrl();

    abstract OVALCompressionMethod getCompressionMethod();

    public boolean isValidVersion(String osVersion) {
        return true;
    }

}
