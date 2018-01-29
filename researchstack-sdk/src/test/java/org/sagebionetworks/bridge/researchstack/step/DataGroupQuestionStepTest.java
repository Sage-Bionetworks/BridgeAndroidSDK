package org.sagebionetworks.bridge.researchstack.step;

import org.junit.Test;
import org.researchstack.backbone.answerformat.AnswerFormat;
import org.researchstack.backbone.answerformat.ChoiceAnswerFormat;

import static org.junit.Assert.*;

/**
 * Created by liujoshua on 1/28/2018.
 */
public class DataGroupQuestionStepTest {
    @Test
    public void setSkipOnSessionContainsAny_NullValue() throws Exception {
        DataGroupQuestionStep step = new DataGroupQuestionStep("id", "title",
                new ChoiceAnswerFormat());

        assertNotNull(step.getSkipOnSessionContainsAny());

        step.setSkipOnSessionContainsAny(null);

        assertNotNull(step.getSkipOnSessionContainsAny());
    }
}