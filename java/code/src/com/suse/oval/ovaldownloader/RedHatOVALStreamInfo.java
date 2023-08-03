package com.suse.oval.ovaldownloader;

public class RedHatOVALStreamInfo extends OVALStreamInfo {
    @Override
    String localFileName() {
        return null;
    }

    @Override
    String remoteFileUrl() {
        return null;
    }

    @Override
    OVALCompressionMethod getCompressionMethod() {
        return null;
    }
}
