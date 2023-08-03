package com.suse.oval.ovaldownloader;

import com.suse.oval.OsFamily;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.zip.GZIPInputStream;

public class OVALDownloader {
    private static final String DOWNLOAD_PATH = "/var/log/rhn/ovals/";

    public File download(OsFamily osFamily, String osVersion) throws IOException {
        OVALStreamInfo streamInfo;
        switch (osFamily) {
            case openSUSE_LEAP:
                streamInfo = new OpenSUSELeapOVALStreamInfo(osVersion);
                break;
            case openSUSE:
                streamInfo = new OpenSUSEOVALStreamInfo(osVersion);
                break;
            default:
                throw new NotImplementedException("Not implemented for " + osFamily);
        }

        if (!streamInfo.isValidVersion(osVersion)) {
            throw new IllegalArgumentException(
                    String.format("Cannot download OVAL for '%s' version '%s'", osFamily, osVersion));
        }

        return doDownload(streamInfo);
    }

    public File doDownload(OVALStreamInfo streamInfo) throws IOException {
        URL remoteOVALFileURL = new URL(streamInfo.remoteFileUrl());
        File localOVALFile = new File(DOWNLOAD_PATH + streamInfo.localFileName() +
                        streamInfo.getCompressionMethod().extension());

        FileUtils.copyURLToFile(remoteOVALFileURL, localOVALFile, 5000, 5000);

        if (streamInfo.getCompressionMethod() == OVALCompressionMethod.GZIP) {
            File uncompressedOVALFile = new File(DOWNLOAD_PATH + streamInfo.localFileName() + ".xml");
            decompressGzip(localOVALFile, uncompressedOVALFile);
            localOVALFile = uncompressedOVALFile;
        }

        return localOVALFile;
    }

    /**
     * Decompress {@code archive} into {@code target} file.
     */
    public static void decompressGzip(File archive, File target) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(archive));
             FileOutputStream fos = new FileOutputStream(target)) {

            // copy GZIPInputStream to FileOutputStream
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }
}
