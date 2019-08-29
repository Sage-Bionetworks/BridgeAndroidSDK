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
import org.sagebionetworks.researchstack.backbone.answerformat.AnswerFormat;
import org.sagebionetworks.researchstack.backbone.answerformat.ChoiceAnswerFormat;
import org.sagebionetworks.researchstack.backbone.model.Choice;
import org.sagebionetworks.researchstack.backbone.model.survey.SurveyItem;
import org.sagebionetworks.researchstack.backbone.model.survey.factory.SurveyFactory;
import org.sagebionetworks.researchstack.backbone.onboarding.OnboardingManager;
import org.sagebionetworks.researchstack.backbone.step.Step;
import org.sagebionetworks.bridge.researchstack.step.DataGroupQuestionStep;
import org.sagebionetworks.bridge.researchstack.survey.DataGroupQuestionSurveyItem;

/**
 * BridgeOnboardingManager overrides createCustomStep() to enable Bridge-specific Onboarding steps,
 * such as data group settings based on onboarding questions. Apps that want these features should
 * subclass BridgeOnboardingManager instead of OnboardingManager.
 */
public class BridgeOnboardingManager extends OnboardingManager {

    private BridgeSurveyFactory bridgeSurveyFactory;

    /**
     * Initializes the BridgeOnboardingManager and the superclass based on the provided context.
     *
     * @param context used in reference to the ResourceManager to load JSON resources to construct
     *                the onboarding manager
     */
    public BridgeOnboardingManager(Context context) {
        super(context);
        bridgeSurveyFactory = new BridgeSurveyFactory();
    }

    /** Create custom onboarding steps for Bridge. */
    @Override
    public Step createCustomStep(
            Context context, SurveyItem item, boolean isSubtaskStep, SurveyFactory factory) {
        return bridgeSurveyFactory.createSurveyStep(context, item, isSubtaskStep);
    }
}
