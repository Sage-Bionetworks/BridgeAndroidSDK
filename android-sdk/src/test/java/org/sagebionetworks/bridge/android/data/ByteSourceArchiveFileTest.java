package org.sagebionetworks.bridge.android.data;

import com.google.common.io.ByteSource;

import org.joda.time.DateTime;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Created by jyliu on 2/2/2017.
 */
public class ByteSourceArchiveFileTest {
    private static final String FILENAME = "archive-file-1.json";
    private static final DateTime END_DATE = DateTime.now();
    private static final ByteSource BYTE_SOURCE = ByteSource.wrap(new byte[]{1, 2, 3});

    @Test
    public void testCreationFromByteSource() throws IOException {
        ByteSourceArchiveFile archiveFile = new ByteSourceArchiveFile(FILENAME, END_DATE, BYTE_SOURCE);

        assertEquals(FILENAME, archiveFile.getFilename());
        assertEquals(END_DATE, archiveFile.getEndDate());
        assertEquals(BYTE_SOURCE, archiveFile.getByteSource());
    }
}