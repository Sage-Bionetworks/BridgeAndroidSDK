package org.sagebionetworks.bridge.android.data;

import com.google.common.io.ByteSource;

import org.joda.time.DateTime;

/**
 * Data measurement file for an archive.
 */
public interface ArchiveFile {
    /**
     * @return name of file
     */
    String getFileName();

    /**
     * @return timestamp representing when file's data was measured and written, if data was
     * measured over a range, timestamp should represent the end of the measured range
     */
    DateTime endDate();

    /**
     * @return measured data
     */
    ByteSource getByteSource();
}
