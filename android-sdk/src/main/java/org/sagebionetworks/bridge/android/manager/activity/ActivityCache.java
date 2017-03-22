package org.sagebionetworks.bridge.android.manager.activity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.Maps;

import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.ActivityType;
import org.sagebionetworks.bridge.rest.model.CompoundActivity;
import org.sagebionetworks.bridge.rest.model.SchemaReference;
import org.sagebionetworks.bridge.rest.model.SurveyReference;
import org.sagebionetworks.bridge.rest.model.TaskReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by jyliu on 3/21/2017.
 */

public class ActivityCache {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityCache.class);
    @NonNull
    private final Map<String, SchemaReference> taskIdentifierToSchema;
    @NonNull
    private final Map<String, SurveyReference> surveyIdentifierToSurvey;
    @NonNull
    private final Map<String, String> surveyGuidToIdentifier;
    @NonNull
    private final Map<String, CompoundActivity> taskIdentifierToCompoundActivity;
    @NonNull
    private final Map<String, ActivityType> identifierToActivityType;


    public ActivityCache() {
        taskIdentifierToSchema = Maps.newHashMap();
        surveyIdentifierToSurvey = Maps.newHashMap();
        surveyGuidToIdentifier = Maps.newHashMap();
        taskIdentifierToCompoundActivity = Maps.newHashMap();
        identifierToActivityType = Maps.newHashMap();

        addTasks();
    }

    private void addTasks() {
        // TODO: this should be configurable
        identifierToActivityType.put("1-APHMedicationTracker-20EF8ED2-E461-4C20-9024-F43FCAAAF4C3", ActivityType.TASK);
        taskIdentifierToSchema.put("1-APHMedicationTracker-20EF8ED2-E461-4C20-9024-F43FCAAAF4C3",
                new SchemaReference().id("Medication Tracker").revision(8L));

        identifierToActivityType.put("1-APHTremor-108E189F-4B5B-48DC-BFD7-FA6796EEf439", ActivityType.TASK);
        taskIdentifierToSchema.put("1-APHTremor-108E189F-4B5B-48DC-BFD7-FA6796EEf439",
                new SchemaReference().id("Tremor Activity").revision(3L));

        identifierToActivityType.put("2-APHIntervalTapping-7259AC18-D711-47A6-ADBD-6CFCECDED1DF", ActivityType.TASK);
        taskIdentifierToSchema.put("2-APHIntervalTapping-7259AC18-D711-47A6-ADBD-6CFCECDED1DF",
                new SchemaReference().id("Tapping Activity").revision(11L));

        identifierToActivityType.put("3-APHMoodSurvey-7259AC18-D711-47A6-ADBD-6CFCECDED1DF", ActivityType.TASK);
        taskIdentifierToSchema.put("3-APHMoodSurvey-7259AC18-D711-47A6-ADBD-6CFCECDED1DF",
                new SchemaReference().id("Mood Survey").revision(2L));

        identifierToActivityType.put("3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292", ActivityType.TASK);
        taskIdentifierToSchema.put("3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292",
                new SchemaReference().id("Voice Activity").revision(4L));

        identifierToActivityType.put("4-APHTimedWalking-80F09109-265A-49C6-9C5D-765E49AAF5D9", ActivityType.TASK);
        taskIdentifierToSchema.put("4-APHTimedWalking-80F09109-265A-49C6-9C5D-765E49AAF5D9",
                new SchemaReference().id("Walking Activity").revision(6L));
    }

    public void cacheActivity(Activity activity) {
        switch (activity.getActivityType()) {
            case TASK:
                TaskReference taskReference = activity.getTask();
                if (taskReference != null) {
                    SchemaReference schemaReference = taskReference.getSchema();
                    if (schemaReference != null) {
                        identifierToActivityType.put(taskReference.getIdentifier(), ActivityType.TASK);
                        taskIdentifierToSchema.put(taskReference.getIdentifier(), schemaReference);
                    }
                }
                break;
            case SURVEY:
                SurveyReference surveyReference = activity.getSurvey();
                if (surveyReference != null) {
                    identifierToActivityType.put(surveyReference.getGuid(), ActivityType.SURVEY);
                    surveyGuidToIdentifier.put(surveyReference.getGuid(), surveyReference.getIdentifier());
                    surveyIdentifierToSurvey.put(surveyReference.getIdentifier(), surveyReference);
                }
                break;
            case COMPOUND:
                CompoundActivity compoundActivity = activity.getCompoundActivity();
                if (compoundActivity != null) {
                    identifierToActivityType.put(compoundActivity.getTaskIdentifier(), ActivityType.COMPOUND);
                    taskIdentifierToCompoundActivity.put(compoundActivity.getTaskIdentifier(), compoundActivity);
                }
                break;
            default:
                LOG.warn("Unhandled activity type:" + activity.getActivityType());
        }
    }

    @Nullable
    public ActivityType getActivityType(String identifier) {
        return identifierToActivityType.get(identifier);
    }

    @Nullable
    public SchemaReference getSchemaReference(String taskIdentifier) {
        return taskIdentifierToSchema.get(taskIdentifier);
    }

    @Nullable
    public String getSurveyIdentifier(String guid) {
        return surveyGuidToIdentifier.get(guid);
    }

    @Nullable
    public SurveyReference getSurvey(String surveyIdentifier) {
        return surveyIdentifierToSurvey.get(surveyIdentifier);
    }

    @Nullable
    public CompoundActivity getCompoundActivity(String taskIdentifier) {
        return taskIdentifierToCompoundActivity.get(taskIdentifier);
    }
}
