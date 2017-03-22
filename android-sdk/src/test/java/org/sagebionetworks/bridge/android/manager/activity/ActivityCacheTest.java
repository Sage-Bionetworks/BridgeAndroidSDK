package org.sagebionetworks.bridge.android.manager.activity;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.android.manager.activity.ActivityCache;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.SchemaReference;
import org.sagebionetworks.bridge.rest.model.TaskReference;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.rest.model.ActivityType.TASK;

/**
 * Created by jyliu on 3/21/2017.
 */
public class ActivityCacheTest {
    private static final String TASK_ID = "taskIdentifier";
    private static final String SCHEMA_ID = "schemaIdentifier";
    private static final Long SCHEMA_REVISION = 10L;

    private ActivityCache activityCache;

    @Before
    public void setupTest() {
        activityCache = new ActivityCache();
    }

    @Test
    public void cacheTask() throws Exception {
        Activity activity = new Activity()
                .activityType(TASK)
                .task(new TaskReference()
                        .identifier(TASK_ID)
                        .schema(new SchemaReference()
                                .id(SCHEMA_ID)
                                .revision(SCHEMA_REVISION)));


        activityCache.cacheActivity(activity);


        assertEquals(TASK, activityCache.getActivityType(TASK_ID));

        SchemaReference schemaReference =  activityCache.getSchemaReference(TASK_ID);
        assertEquals(SCHEMA_ID, schemaReference.getId());
        assertEquals(SCHEMA_REVISION, schemaReference.getRevision());
    }

}