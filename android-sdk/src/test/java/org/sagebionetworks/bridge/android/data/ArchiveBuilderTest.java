package org.sagebionetworks.bridge.android.data;

import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.rest.RestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Created by jyliu on 1/30/2017.
 */
public class ArchiveBuilderTest {
    private static final Logger LOG = LoggerFactory.getLogger(ArchiveBuilderTest.class);
    private static final String APP_VERSION_NAME = "version 1.0, build 9";
    private static final String DEVICE_NAME = "device";

    private static final byte[] BYTES = new byte[]{1, 2, 3, 4};

    @Mock
    private BridgeConfig bridgeConfig;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(bridgeConfig.getDeviceName()).thenReturn(DEVICE_NAME);
        when(bridgeConfig.getAppVersionName()).thenReturn(APP_VERSION_NAME);

    }

    @Test
    public void testBuildSurvey() throws IOException {
        String surveyGuid = "survey";
        DateTime surveyCreatedOn = DateTime.now();


        String filename1 = "file1";
        DateTime endTime1 = DateTime.now().minusHours(1);
        String json = "{'key' : 'value'}";

        String filename2 = "file2";
        DateTime endTime2 = DateTime.now().minusHours(2);
        ByteSource byteSource = ByteSource.wrap(BYTES);

        ArchiveFile file1 = new JsonArchiveFile(filename1, endTime1, json);
        ArchiveFile file2 = new ByteSourceArchiveFile(filename2, endTime2, byteSource);

        Archive archive = Archive.Builder.forSurvey(surveyGuid, surveyCreatedOn)
                .withBridgeConfig(bridgeConfig)
                .addDataFile(file1)
                .addDataFile(file2)
                .build();

        ByteArrayOutputStream zipOutput = new ByteArrayOutputStream();
        archive.writeTo(zipOutput);

        Map<String, ByteArrayOutputStream> map = Maps.newHashMap();
        byte[] buffer = new byte[1024];

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipOutput.toByteArray()));
        ZipEntry zipEntry = zis.getNextEntry();
        try {
            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                LOG.info("filename: " + fileName);

                ByteArrayOutputStream fileBytes = new ByteArrayOutputStream();
                int len = 0;
                while ((len = zis.read(buffer)) > 0) {
                    LOG.info("length: " + len);
                    fileBytes.write(buffer, 0, len);
                }

                map.put(fileName, fileBytes);
                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }
        } finally {
            zis.close();
        }

        ByteArrayOutputStream bytes1 = map.get(filename1);
        assertNotNull(bytes1);
        assertEquals(json, new String(bytes1.toByteArray()));

        ByteArrayOutputStream bytes2 = map.get(filename2);
        assertNotNull(bytes2);
        assertArrayEquals(BYTES, bytes2.toByteArray());

        ByteArrayOutputStream bytesInfo = map.get("info.json");
        assertNotNull(bytesInfo);

        ArchiveInfo info = RestUtils.GSON.fromJson(bytesInfo.toString(), ArchiveInfo.class);

        assertEquals(APP_VERSION_NAME, info.appVersion);
        assertEquals(DEVICE_NAME, info.phoneInfo);
        List<ArchiveInfo.FileInfo> files = info.files;

        ArchiveInfo.FileInfo info1 = files.get(0);
        assertFileInfoForFile(file1, info1, bytes1.toByteArray());

        ArchiveInfo.FileInfo info2 = files.get(1);
        assertFileInfoForFile(file2, info2, bytes2.toByteArray());
    }

    private void assertFileInfoForFile(ArchiveFile file, ArchiveInfo.FileInfo info, byte[] contents) throws IOException {
        assertEquals(file.getFilename(), info.filename);
        assertTrue(file.getEndDate().isEqual(info.timestamp));
        assertArrayEquals(file.getByteSource().read(), contents);
    }
}