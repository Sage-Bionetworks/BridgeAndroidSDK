package org.sagebionetworks.bridge.android.data;

import com.google.common.io.ByteSource;
import com.google.common.io.Files;

import org.joda.time.DateTime;

import java.io.File;

/**
 * Created by jyliu on 2/2/2017.
 */
public class ByteSourceArchiveFile implements ArchiveFile {
    private final String filename;
    private final DateTime endDate;
    private final ByteSource byteSource;

    public ByteSourceArchiveFile(String filename, DateTime endDate, ByteSource byteSource) {
        this.filename = filename;
        this.endDate = endDate;
        this.byteSource = byteSource;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public DateTime getEndDate() {
        return endDate;
    }

    @Override
    public ByteSource getByteSource() {
        return byteSource;
    }
}
