package org.sagebionetworks.bridge.android.data;

import com.google.common.base.Charsets;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.rest.RestUtils;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Created by jyliu on 2/2/2017.
 */
public class JsonArchiveFileTest {
    private static final String FILENAME = "archive-file-1.json";
    private static final DateTime END_DATE = DateTime.now();

    private static final String JSON = "{'key1' : 'value1'}";
    private static final ArchiveInfo TEST_OBJECT;

    static {
        TEST_OBJECT = new ArchiveInfo();
        TEST_OBJECT.appVersion = "appVersion";
        TEST_OBJECT.schemaRevision = 5;
        TEST_OBJECT.item = "some-activity";
        TEST_OBJECT.phoneInfo = "smart phone";
    }

    @Test
    public void testCreationFromString() throws IOException {
        JsonArchiveFile archiveFile = new JsonArchiveFile(FILENAME, END_DATE, JSON);

        assertEquals(FILENAME, archiveFile.getFilename());
        assertEquals(END_DATE, archiveFile.getEndDate());
        assertEquals(JSON, archiveFile.getByteSource().asCharSource(Charsets.UTF_8).read());
    }

    @Test
    public void testCreationFromObject() throws IOException {
        JsonArchiveFile archiveFile = new JsonArchiveFile(FILENAME, END_DATE, TEST_OBJECT);

        assertEquals(FILENAME, archiveFile.getFilename());
        assertEquals(END_DATE, archiveFile.getEndDate());

        String json = archiveFile.getByteSource().asCharSource(Charsets.UTF_8).read();
        assertEquals(TEST_OBJECT, RestUtils.GSON.fromJson(json, ArchiveInfo.class));
    }

    @Test
    public void testCreationFromObjectWithType() throws IOException {
        JsonArchiveFile archiveFile = new JsonArchiveFile(FILENAME, END_DATE, TEST_OBJECT, ArchiveInfo.class);

        assertEquals(FILENAME, archiveFile.getFilename());
        assertEquals(END_DATE, archiveFile.getEndDate());

        String json = archiveFile.getByteSource().asCharSource(Charsets.UTF_8).read();
        assertEquals(TEST_OBJECT, RestUtils.GSON.fromJson(json, ArchiveInfo.class));
    }

}