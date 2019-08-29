package org.sagebionetworks.bridge.researchstack.task.creation;

import android.support.annotation.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;

import org.sagebionetworks.researchstack.backbone.model.survey.FormSurveyItem;
import org.sagebionetworks.researchstack.backbone.model.survey.SurveyItem;
import org.sagebionetworks.researchstack.backbone.model.survey.SurveyItemAdapter;
import org.sagebionetworks.bridge.researchstack.survey.DataGroupQuestionSurveyItem;

import java.util.Map;

/** Subclasses SurveyItemAdapter to enable custom survey and onboarding question types. */
public class BridgeSurveyItemAdapter extends SurveyItemAdapter {

    // iOS had this one be the only "type" custom one, the rest are normal supported types,
    // but with additional json fields, which we will deal with in CustomStepCreation
    public static final String TRACKED_SELECTION_TYPE_GSON = "trackingSelection";

    @VisibleForTesting
    static final Map<String, Class<? extends SurveyItem>> TYPE_TO_CLASS =
            ImmutableMap.<String, Class<? extends SurveyItem>>builder()
                    .put(DataGroupQuestionSurveyItem.CUSTOM_TYPE, DataGroupQuestionSurveyItem.class)
                    .put(TRACKED_SELECTION_TYPE_GSON, FormSurveyItem.class)
                    .build();

    @Override
    public Class<? extends SurveyItem> getCustomClass(String customType, JsonElement json) {
        if (customType != null && TYPE_TO_CLASS.containsKey(customType)) {
            return TYPE_TO_CLASS.get(customType);
        } else {
            return super.getCustomClass(customType, json);
        }
    }
}
