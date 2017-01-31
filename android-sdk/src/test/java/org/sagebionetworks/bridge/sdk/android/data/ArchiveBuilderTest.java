package org.sagebionetworks.bridge.sdk.android.data;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.data.Archive;
import org.sagebionetworks.bridge.android.data.ArchiveFile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by jyliu on 1/30/2017.
 */
public class ArchiveBuilderTest {
    private static final String APP_VERSION_NAME = "version 1.0, build 9";
    private static final String DEVICE_NAME = "device";

    @Mock
    private BridgeConfig bridgeConfig;

    @Before
    public void setup() {
        when(bridgeConfig.getDeviceName()).thenReturn(DEVICE_NAME);
        when(bridgeConfig.getAppVersionName()).thenReturn(APP_VERSION_NAME);

    }

    @Test
    public void testBuildSurvey() {
        String surveyGuid = "survey";
        DateTime surveyCreatedOn = DateTime.now();

        ArchiveFile file1 = mock(ArchiveFile.class);
        ArchiveFile file2 = mock(ArchiveFile.class);

        Archive archive = Archive.Builder.forSurvey(surveyGuid, surveyCreatedOn)
                .withBridgeConfig(bridgeConfig)
                .addDataFile(file1)
                .addDataFile(file2).build();

    }
}