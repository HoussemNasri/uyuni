package com.suse.oval.ovaldownloader;

public enum OVALCompressionMethod {
    GZIP(".gz"), NOT_COMPRESSED(".xml");
    private final String extension;

    OVALCompressionMethod(String extension) {
        this.extension = extension;
    }

    String extension() {
        return extension;
    }
}
