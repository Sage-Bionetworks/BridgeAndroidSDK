package org.sagebionetworks.bridge.android.manager.upload;

import static com.google.common.base.Preconditions.checkNotNull;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.TaskReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ArchiveUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveUtil.class);

    private ArchiveUtil() {
    }

    @NonNull
    public static JsonArchiveFile createMetaDataFile(@NonNull ScheduledActivity scheduledActivity,
            @NonNull ImmutableList<String> dataGroups) {
        checkNotNull(scheduledActivity);
        checkNotNull(dataGroups);

        Map<String, Object> metaDataMap = new HashMap<>();

        // Set end data
        DateTime endDate = DateTime.now();
        if (scheduledActivity.getFinishedOn() != null) {
            endDate = scheduledActivity.getFinishedOn();
        }
        metaDataMap.put("endDate", endDate);

        // Set metadata key/values
        if (scheduledActivity.getActivity() != null &&
                scheduledActivity.getActivity().getTask() != null) {

            Activity activity = scheduledActivity.getActivity();
            TaskReference taskRef = activity.getTask();

            String taskIdentifier = taskRef.getIdentifier();
            if (taskIdentifier != null) {
                metaDataMap.put("taskIdentifier", taskIdentifier);
            }

            if (activity.getLabel() != null) {
                metaDataMap.put("activityLabel", activity.getLabel());
            }
        }

        if (scheduledActivity.getGuid() != null) {
            metaDataMap.put("scheduledActivityGuid", scheduledActivity.getGuid());
        }

        // iOS Needs a task run UUID, so just send one up for compliance,
        // Android only uses UUID for active steps, and not tasks
        metaDataMap.put("taskRunUUID", UUID.randomUUID().toString());

        if (scheduledActivity.getStartedOn() != null) {
            metaDataMap.put("startDate", scheduledActivity.getStartedOn());
        }

        if (scheduledActivity.getScheduledOn() != null) {
            metaDataMap.put("scheduledOn", scheduledActivity.getScheduledOn().toDate());
        }

        if (scheduledActivity.getSchedulePlanGuid() != null) {
            metaDataMap.put("scheduleIdentifier", scheduledActivity.getSchedulePlanGuid());
        }

        metaDataMap.put("dataGroups", TextUtils.join(",", dataGroups));

        String metaDataJson = RestUtils.GSON.toJson(metaDataMap);
        return new JsonArchiveFile("metadata.json", endDate, metaDataJson);
    }
}
