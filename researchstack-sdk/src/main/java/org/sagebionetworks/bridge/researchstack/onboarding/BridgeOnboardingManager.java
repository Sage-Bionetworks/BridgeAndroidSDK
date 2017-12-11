/*
 *    Copyright 2017 Sage Bionetworks
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.sagebionetworks.bridge.researchstack.onboarding;

import android.content.Context;
import org.researchstack.backbone.answerformat.AnswerFormat;
import org.researchstack.backbone.answerformat.ChoiceAnswerFormat;
import org.researchstack.backbone.model.Choice;
import org.researchstack.backbone.model.survey.SurveyItem;
import org.researchstack.backbone.model.survey.factory.SurveyFactory;
import org.researchstack.backbone.onboarding.OnboardingManager;
import org.researchstack.backbone.step.Step;
import org.sagebionetworks.bridge.researchstack.step.DataGroupQuestionStep;
import org.sagebionetworks.bridge.researchstack.survey.DataGroupQuestionSurveyItem;

/**
 * BridgeOnboardingManager overrides createCustomStep() to enable Bridge-specific Onboarding steps,
 * such as data group settings based on onboarding questions. Apps that want these features should
 * subclass BridgeOnboardingManager instead of OnboardingManager.
 */
public class BridgeOnboardingManager extends OnboardingManager {
    /**
     * Initializes the BridgeOnboardingManager and the superclass based on the provided context.
     *
     * @param context used in reference to the ResourceManager to load JSON resources to construct
     *                the onboarding manager
     */
    public BridgeOnboardingManager(Context context) {
        super(context);
    }

    /** Create custom onboarding steps for Bridge. */
    @Override
    public Step createCustomStep(
            Context context, SurveyItem item, boolean isSubtaskStep, SurveyFactory factory) {
        if (DataGroupQuestionSurveyItem.CUSTOM_TYPE.equals(item.getCustomTypeValue())) {
            // Basic validation
            if (!(item instanceof DataGroupQuestionSurveyItem)) {
                throw new IllegalStateException("Error in json parsing, this type must be " +
                        "DataGroupQuestionSurveyItem");
            }
            DataGroupQuestionSurveyItem dataGroupItem = (DataGroupQuestionSurveyItem) item;
            if (dataGroupItem.items == null || dataGroupItem.items.isEmpty()) {
                throw new IllegalStateException("DataGroupQuestionSurveyItem must have choices");
            }

            // Create answer format for this question
            AnswerFormat.ChoiceAnswerStyle answerStyle = AnswerFormat.ChoiceAnswerStyle
                    .SingleChoice;
            Choice[] choices = dataGroupItem.items.toArray(new Choice[dataGroupItem.items.size()]);
            AnswerFormat format = new ChoiceAnswerFormat(answerStyle, choices);

            // Create the question step and apply navigation rules
            DataGroupQuestionStep dataGroupStep = dataGroupsQuestionStep(
                    dataGroupItem.identifier, dataGroupItem.title, format);
            dataGroupStep.setExpectedAnswer(dataGroupItem.expectedAnswer);
            dataGroupStep.setOptional(dataGroupItem.optional);
            dataGroupStep.setPlaceholder(dataGroupItem.placeholderText);
            dataGroupStep.setShouldPersist(dataGroupItem.shouldPersist());
            dataGroupStep.setShouldSkipIfSessionContainsDataGroups(
                    dataGroupItem.shouldSkipIfSessionContainsDataGroups());
            dataGroupStep.setSkipToStepIdentifier(dataGroupItem.skipIdentifier);
            dataGroupStep.setSkipIfPassed(dataGroupItem.skipIfPassed);
            dataGroupStep.setText(dataGroupItem.text);

            return dataGroupStep;
        } else {
            // For everything else, fallback to the superclass's createCustomStep.
            return super.createCustomStep(context, item, isSubtaskStep, factory);
        }
    }

    protected DataGroupQuestionStep dataGroupsQuestionStep(String identifier, String title, AnswerFormat format) {
        return new DataGroupQuestionStep(identifier, title, format);
    }
}
