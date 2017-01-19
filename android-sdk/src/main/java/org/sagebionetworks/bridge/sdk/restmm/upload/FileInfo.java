package org.sagebionetworks.bridge.sdk.restmm.upload;

public class FileInfo {
    private String filename;
    private String timestamp;

    public FileInfo(String filename, String timestamp) {
        this.filename = filename;
        this.timestamp = timestamp;
    }
}
