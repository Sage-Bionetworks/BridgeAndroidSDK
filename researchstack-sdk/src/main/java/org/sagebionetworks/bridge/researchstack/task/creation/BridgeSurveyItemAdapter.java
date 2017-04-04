package org.sagebionetworks.bridge.researchstack.task.creation;

import com.google.gson.JsonElement;

import org.researchstack.backbone.model.survey.CompoundQuestionSurveyItem;
import org.researchstack.backbone.model.survey.SurveyItem;
import org.researchstack.backbone.model.survey.SurveyItemAdapter;

/**
 * Created by TheMDP on 3/25/17.
 */

public class BridgeSurveyItemAdapter extends SurveyItemAdapter {

    public static final String TRACKED_SELECTION_TYPE_GSON = "trackingSelection";

    @Override
    public Class<? extends SurveyItem> getCustomClass(String customType, JsonElement json) {

        // iOS had this one be the only "type" custom one, the rest are normal supported types,
        // but with additional json fields, which we will deal with in CustomStepCreation
        switch (customType) {
            case TRACKED_SELECTION_TYPE_GSON:
                return CompoundQuestionSurveyItem.class;
        }

        return super.getCustomClass(customType, json);
    }
}
