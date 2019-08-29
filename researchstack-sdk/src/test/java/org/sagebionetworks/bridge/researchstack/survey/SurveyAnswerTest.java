package org.sagebionetworks.bridge.researchstack.survey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.sagebionetworks.researchstack.backbone.answerformat.UnknownAnswerFormat;
import org.sagebionetworks.researchstack.backbone.result.StepResult;
import org.sagebionetworks.researchstack.backbone.step.QuestionStep;

public class SurveyAnswerTest {
    @Test
    public void choiceAnswers_list() {
        StepResult stepResult = makeStepResult(ImmutableList.of("foo", "bar"));
        SurveyAnswer.ChoiceSurveyAnswer choiceSurveyAnswer = new SurveyAnswer.ChoiceSurveyAnswer(stepResult);

        List<?> answerList = choiceSurveyAnswer.choiceAnswers;
        assertEquals(2, answerList.size());
        assertEquals("foo", answerList.get(0));
        assertEquals("bar", answerList.get(1));
    }

    @Test
    public void choiceAnswers_emptyList() {
        StepResult stepResult = makeStepResult(ImmutableList.of());
        SurveyAnswer.ChoiceSurveyAnswer choiceSurveyAnswer = new SurveyAnswer.ChoiceSurveyAnswer(stepResult);
        assertTrue(choiceSurveyAnswer.choiceAnswers.isEmpty());
    }

    @Test
    public void choiceAnswers_array() {
        StepResult stepResult = makeStepResult(new String[] { "foo", "bar" });
        SurveyAnswer.ChoiceSurveyAnswer choiceSurveyAnswer = new SurveyAnswer.ChoiceSurveyAnswer(stepResult);

        List<?> answerList = choiceSurveyAnswer.choiceAnswers;
        assertEquals(2, answerList.size());
        assertEquals("foo", answerList.get(0));
        assertEquals("bar", answerList.get(1));
    }

    @Test
    public void choiceAnswers_emptyArray() {
        StepResult stepResult = makeStepResult(new String[] {});
        SurveyAnswer.ChoiceSurveyAnswer choiceSurveyAnswer = new SurveyAnswer.ChoiceSurveyAnswer(stepResult);
        assertTrue(choiceSurveyAnswer.choiceAnswers.isEmpty());
    }

    @Test
    public void choiceAnswers_scalar() {
        StepResult stepResult = makeStepResult("foo");
        SurveyAnswer.ChoiceSurveyAnswer choiceSurveyAnswer = new SurveyAnswer.ChoiceSurveyAnswer(stepResult);

        List<?> answerList = choiceSurveyAnswer.choiceAnswers;
        assertEquals(1, answerList.size());
        assertEquals("foo", answerList.get(0));
    }

    @Test
    public void choiceAnswers_null() {
        StepResult stepResult = makeStepResult(null);
        SurveyAnswer.ChoiceSurveyAnswer choiceSurveyAnswer = new SurveyAnswer.ChoiceSurveyAnswer(stepResult);
        assertTrue(choiceSurveyAnswer.choiceAnswers.isEmpty());
    }

    @SuppressWarnings({ "deprecation", "unchecked" })
    private static StepResult makeStepResult(Object result) {
        // We need a question step with an answer format.
        QuestionStep step = new QuestionStep();
        step.setAnswerFormat(new UnknownAnswerFormat());

        // Make step result.
        StepResult stepResult = new StepResult<>(step);
        stepResult.setResult(result);
        return stepResult;
    }
}
