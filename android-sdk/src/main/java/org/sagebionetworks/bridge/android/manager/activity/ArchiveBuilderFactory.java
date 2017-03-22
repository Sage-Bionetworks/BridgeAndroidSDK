package org.sagebionetworks.bridge.android.manager.activity;

import android.support.annotation.NonNull;

import org.sagebionetworks.bridge.android.BridgeConfig;
import org.sagebionetworks.bridge.android.data.Archive;
import org.sagebionetworks.bridge.rest.model.ActivityType;
import org.sagebionetworks.bridge.rest.model.SchemaReference;
import org.sagebionetworks.bridge.rest.model.SurveyReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.sagebionetworks.bridge.rest.model.ActivityType.SURVEY;
import static org.sagebionetworks.bridge.rest.model.ActivityType.TASK;

/**
 * Created by jyliu on 3/21/2017.
 */

public class ArchiveBuilderFactory {
    Logger logger = LoggerFactory.getLogger(ArchiveBuilderFactory.class);

    @NonNull
    private final BridgeConfig bridgeConfig;
    @NonNull
    private final ActivityCache activityCache;

    public ArchiveBuilderFactory(BridgeConfig bridgeConfig, ActivityCache activityCache) {
        this.bridgeConfig = bridgeConfig;
        this.activityCache = activityCache;
    }

    public Archive.Builder create(String identifier) {

        ActivityType activityType = activityCache.getActivityType(identifier);

        if (TASK == activityType) {
            logger.debug("Found task");

            SchemaReference schemaReference = activityCache.getSchemaReference(identifier);
            if (schemaReference != null) {
                logger.debug("Found task with schema: " + schemaReference);

                Long revision = schemaReference.getRevision();
                if (revision == null) {
                    return Archive.Builder
                            .forActivity(schemaReference.getId())
                            .withBridgeConfig(bridgeConfig);
                } else {
                    return Archive.Builder
                            .forActivity(schemaReference.getId(), revision.intValue())
                            .withBridgeConfig(bridgeConfig);
                }
            }
            logger.debug("No schema found for task");
        } else if (SURVEY == activityType) {
            logger.debug("Found survey");

            String surveyIdentifier = activityCache.getSurveyIdentifier(identifier);

            SurveyReference surveyReference = activityCache.getSurvey(surveyIdentifier);
            if (surveyReference != null) {
                logger.debug("Found survey with SurveyReference: " + surveyReference);
            }
            return Archive.Builder
                    .forSurvey(surveyReference.getIdentifier(), surveyReference.getCreatedOn())
                    .withBridgeConfig(bridgeConfig);
        } else {
            logger.debug("Unhandled activity type: " + activityType);
        }

        return null;
    }
}
