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
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.researchstack.backbone.answerformat.AnswerFormat;
import org.researchstack.backbone.answerformat.ChoiceAnswerFormat;
import org.researchstack.backbone.model.Choice;
import org.researchstack.backbone.model.survey.SurveyItemType;
import org.researchstack.backbone.model.survey.factory.SurveyFactory;
import org.researchstack.backbone.step.Step;
import org.sagebionetworks.bridge.researchstack.step.DataGroupQuestionStep;
import org.sagebionetworks.bridge.researchstack.survey.DataGroupQuestionSurveyItem;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeSurveyFactoryTest {
    @Test
    public void createCustomStep_DataGroupQuestionStep() {
        BridgeSurveyFactory bridgeSurveyFactory = new BridgeSurveyFactory();

        // We don't use Context or SurveyFactory (or isSubtaskStep), but mock them anyway.
        Context mockContext = mock(Context.class);
        SurveyFactory mockSurveyFactory = mock(SurveyFactory.class);

        // Make choices
        List<Choice<String>> choiceList = ImmutableList.of(
                new Choice<>("Choose Foo", "foo"),
                new Choice<>("Choose Bar", "bar"));

        // Make survey item
        DataGroupQuestionSurveyItem item = new DataGroupQuestionSurveyItem();
        item.setCustomTypeValue(DataGroupQuestionSurveyItem.CUSTOM_TYPE);
        item.expectedAnswer = "foo";
        item.identifier = "my-item-id";
        item.items = choiceList;
        item.optional = true;
        item.placeholderText = "My Placeholder Text";
        item.setShouldPersist(true);
        item.skipIdentifier = "next-question-id";
        item.skipIfPassed = true;
        item.text = "My Onboarding Item Text";
        item.title = "My Onboarding Item";
        item.type = SurveyItemType.CUSTOM;

        // Execute and validate
        Step baseStep = bridgeSurveyFactory.createSurveyStep(mockContext, item, false);
        assertTrue(baseStep instanceof DataGroupQuestionStep);
        DataGroupQuestionStep step = (DataGroupQuestionStep)baseStep;

        assertEquals(item.expectedAnswer, step.getExpectedAnswer());
        assertEquals(item.identifier, step.getIdentifier());
        assertEquals(item.optional, step.isOptional());
        assertEquals(item.placeholderText, step.getPlaceholder());
        assertEquals(item.shouldPersist(), step.shouldPersist());
        assertEquals(item.skipIdentifier, step.getSkipToStepIdentifier());
        assertEquals(item.skipIfPassed, step.getSkipIfPassed());
        assertEquals(item.text, step.getText());
        assertEquals(item.title, step.getTitle());

        ChoiceAnswerFormat answerFormat = (ChoiceAnswerFormat) step.getAnswerFormat();
        assertEquals(AnswerFormat.Type.SingleChoice, answerFormat.getQuestionType());
        Choice[] choiceArray = answerFormat.getChoices();
        assertEquals(2, choiceArray.length);
        assertEquals(choiceList.get(0), choiceArray[0]);
        assertEquals(choiceList.get(1), choiceArray[1]);
    }
}
