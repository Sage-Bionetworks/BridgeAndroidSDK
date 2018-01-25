package org.sagebionetworks.bridge.researchstack.onboarding;

import android.content.Context;

import org.researchstack.backbone.answerformat.AnswerFormat;
import org.researchstack.backbone.answerformat.ChoiceAnswerFormat;
import org.researchstack.backbone.model.Choice;
import org.researchstack.backbone.model.survey.SurveyItem;
import org.researchstack.backbone.model.survey.factory.SurveyFactory;
import org.researchstack.backbone.model.taskitem.factory.TaskItemFactory;
import org.researchstack.backbone.step.Step;
import org.sagebionetworks.bridge.researchstack.step.DataGroupQuestionStep;
import org.sagebionetworks.bridge.researchstack.survey.DataGroupQuestionSurveyItem;

/**
 * Created by TheMDP on 12/12/17.
 *
 * The BridgeSurveyFactory will control custom deserialization and step building for this library
 */

public class BridgeSurveyFactory extends TaskItemFactory {

    public BridgeSurveyFactory() {
        super();
        setupCustomStepCreator();
    }

    protected void setupCustomStepCreator() {
        setCustomStepCreator(new BridgeCustomStepCreator());
    }

    protected class BridgeCustomStepCreator implements CustomStepCreator {

        @Override
        public Step createCustomStep(
                Context context, SurveyItem item, boolean isSubtaskStep, SurveyFactory factory) {
            if (item.getCustomTypeValue() != null) {
                switch (item.getCustomTypeValue()) {
                    case DataGroupQuestionSurveyItem.CUSTOM_TYPE:
                        if (!(item instanceof DataGroupQuestionSurveyItem)) {
                            throw new IllegalStateException("Error in json parsing, this type must be " +
                                    "DataGroupQuestionSurveyItem");
                        }
                        return createDataGroupsQuestionStep((DataGroupQuestionSurveyItem)item);
                }
            }
            return null;
        }
    }

    protected DataGroupQuestionStep createDataGroupsQuestionStep(DataGroupQuestionSurveyItem item) {
        if (item.items == null || item.items.isEmpty()) {
            throw new IllegalStateException("DataGroupQuestionSurveyItem must have choices");
        }

        // Create answer format for this question
        AnswerFormat.ChoiceAnswerStyle answerStyle = AnswerFormat.ChoiceAnswerStyle
                .SingleChoice;
        Choice[] choices = item.items.toArray(new Choice[item.items.size()]);
        AnswerFormat format = new ChoiceAnswerFormat(answerStyle, choices);

        // Create the question step and apply navigation rules
        DataGroupQuestionStep step = dataGroupsQuestionStep(item.identifier, item.title, format);
        fillDataGroupsQuestionStep(step, item);

        return step;
    }

    protected void fillDataGroupsQuestionStep(DataGroupQuestionStep step,
                                              DataGroupQuestionSurveyItem item) {
        fillQuestionStep(item, step);
        transferNavigationRules(item, step);
        step.setShouldPersist(item.shouldPersist());
        step.setSkipOnSessionContainsAny(item.getSkipOnSessionContainsAny());
    }

    protected DataGroupQuestionStep dataGroupsQuestionStep(String identifier, String title, AnswerFormat format) {
        return new DataGroupQuestionStep(identifier, title, format);
    }
}
