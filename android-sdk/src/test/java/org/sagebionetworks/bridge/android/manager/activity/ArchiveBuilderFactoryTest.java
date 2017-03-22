package org.sagebionetworks.bridge.android.manager.activity;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.data.Archive;
import org.sagebionetworks.bridge.android.manager.activity.ActivityCache;
import org.sagebionetworks.bridge.android.manager.activity.ArchiveBuilderFactory;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.SchemaReference;
import org.sagebionetworks.bridge.rest.model.TaskReference;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.rest.model.ActivityType.TASK;

/**
 * Created by jyliu on 3/21/2017.
 */
public class ArchiveBuilderFactoryTest {
    private static final String TASK_ID = "taskIdentifier";
    private static final String SCHEMA_ID = "schemaIdentifier";
    private static final Long SCHEMA_REVISION = 10L;

    @Mock
    private BridgeConfig bridgeConfig;

    private ActivityCache activityCache;

    private ArchiveBuilderFactory archiveBuilderFactory;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        activityCache = new ActivityCache();
        archiveBuilderFactory = new ArchiveBuilderFactory(bridgeConfig, activityCache);

        when(bridgeConfig.getAppVersionName()).thenReturn("app version");
        when(bridgeConfig.getDeviceName()).thenReturn("phone type");
    }

    @Test
    public void testMoodSurvey() {
        Archive.Builder builder = archiveBuilderFactory
                .create("3-APHMoodSurvey-7259AC18-D711-47A6-ADBD-6CFCECDED1DF");

        Archive archive = builder.build();

        assertNotNull(archive);
    }

    @Test
    public void testActivity() {
        Activity activity = new Activity()
                .activityType(TASK)
                .task(new TaskReference()
                        .identifier(TASK_ID)
                        .schema(new SchemaReference()
                                .id(SCHEMA_ID)
                                .revision(SCHEMA_REVISION)));

        activityCache.cacheActivity(activity);

        Archive.Builder builder = archiveBuilderFactory.create(TASK_ID);

        Archive archive = builder.build();

        assertNotNull(archive);
    }
}