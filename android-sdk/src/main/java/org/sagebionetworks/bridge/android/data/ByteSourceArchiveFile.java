package org.sagebionetworks.bridge.android.data;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteSourceArchiveFile that = (ByteSourceArchiveFile) o;
        return Objects.equal(filename, that.filename) &&
                Objects.equal(endDate, that.endDate) &&
                Objects.equal(byteSource, that.byteSource);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("filename", filename)
                .add("endDate", endDate)
                .add("byteSource", byteSource)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(filename, endDate, byteSource);
    }
}
