package org.sagebionetworks.bridge.researchstack.data;

import android.os.Parcel;
import android.os.Parcelable;

import org.joda.time.DateTime;
import org.researchstack.backbone.result.TaskResult;
import org.researchstack.backbone.step.Step;
import org.researchstack.backbone.task.Task;
import org.sagebionetworks.bridge.rest.model.ActivityType;

/**
 * Created by jyliu on 3/9/2017.
 */

public class ArchiveTask extends Task {

    private static final String EXTRA_ARCHIVE_TYPE_INFO = ArchiveTypeInfo.class.getCanonicalName();

    public static ArchiveTask forSurvey(Task task, String surveyGuid, DateTime createdOn) {
        return new ArchiveTask(task, new ArchiveTypeInfo(surveyGuid, createdOn));
    }

    public static ArchiveTask forSchema(Task task, String schemaId, Long revision) {
        return new ArchiveTask(task, new ArchiveTypeInfo(schemaId, revision));
    }

    private final Task baseTask;
    private final ArchiveTypeInfo type;

    private ArchiveTask(Task baseTask, ArchiveTypeInfo type) {
        this.baseTask = baseTask;
        this.type = type;
    }

    @Override
    public Step getStepAfterStep(Step step, TaskResult result) {
        // init TaskResult with Bridge Archive metadata on first step
        if (step == null) {
            result.
        }
        return baseTask.getStepAfterStep(step, result);
    }

    @Override
    public Step getStepBeforeStep(Step step, TaskResult result) {
        return baseTask.getStepAfterStep(step, result);
    }

    @Override
    public Step getStepWithIdentifier(String identifier) {
        return baseTask.getStepWithIdentifier(identifier);
    }

    @Override
    public TaskProgress getProgressOfCurrentStep(Step step, TaskResult result) {
        return baseTask.getProgressOfCurrentStep(step, result);
    }

    @Override
    public void validateParameters() {
        baseTask.validateParameters();
    }

    public static class ArchiveTypeInfo implements Parcelable {

        private final ActivityType activityType;

        // survey
        private final String surveyGuid;
        private final DateTime createdOn;

        // task
        private final String schemaId;
        private final Long revision;

        ArchiveTypeInfo(String surveyGuid, DateTime createdOn) {
            this.activityType = ActivityType.SURVEY;
            this.surveyGuid = surveyGuid;
            this.createdOn = createdOn;
            this.schemaId = null;
            this.revision = null;
        }

        ArchiveTypeInfo(String schemaId, Long revision) {
            this.activityType = ActivityType.TASK;
            this.surveyGuid = null;
            this.createdOn = null;
            this.schemaId = schemaId;
            this.revision = revision;
        }

        ArchiveTypeInfo(Parcel source) {
            this.activityType = ActivityType.valueOf(source.readString());
            this.surveyGuid = source.readString();
            this.createdOn = new DateTime(source.readLong());
            this.schemaId = source.readString();
            this.revision = (Long) source.readValue(Long.class.getClassLoader());
        }

        public static final Parcelable.Creator<ArchiveTypeInfo> CREATOR =
                new Creator<ArchiveTypeInfo>() {
                    @Override
                    public ArchiveTypeInfo createFromParcel(Parcel source) {
                        return new ArchiveTypeInfo(source);
                    }

                    @Override
                    public ArchiveTypeInfo[] newArray(int size) {
                        return new ArchiveTypeInfo[size];
                    }
                };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(activityType.name());
            dest.writeString(surveyGuid);
            dest.writeLong(createdOn.getMillis());
            dest.writeString(schemaId);
            dest.writeValue(revision);
        }
    }
}
