package org.sagebionetworks.bridge.researchstack.survey;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import org.researchstack.backbone.answerformat.AnswerFormat;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.utils.FormatHelper;

import java.util.Date;
import java.util.List;

public class SurveyAnswer {
  public int questionType;
  public String questionTypeName;
  public final String startDate;
  public final String item;
  public final String endDate;

  public SurveyAnswer(StepResult stepResult) {
    // Custom implementations of SurveyAnswer may not have the AnswerFormat.Type enum value
    if (stepResult.getAnswerFormat().getQuestionType() instanceof AnswerFormat.Type) {
      AnswerFormat.Type type = (AnswerFormat.Type) stepResult.getAnswerFormat().getQuestionType();
      this.questionType = type.ordinal();
      this.questionTypeName = type.name();
    }
    this.startDate = FormatHelper.DEFAULT_FORMAT.format(stepResult.getStartDate());
    this.item = stepResult.getIdentifier();
    this.endDate = FormatHelper.DEFAULT_FORMAT.format(stepResult.getEndDate());
  }

  public static class BooleanSurveyAnswer extends SurveyAnswer {

    private final Boolean booleanAnswer;

    public BooleanSurveyAnswer(StepResult result) {
      super(result);
      booleanAnswer = (Boolean) result.getResult();
    }
  }

  public static class ChoiceSurveyAnswer<T> extends SurveyAnswer {
    public final List<T> choiceAnswers;

    public ChoiceSurveyAnswer(StepResult<T> stepResult) {
      super(stepResult);

      T result = stepResult.getResult();
      if (result instanceof List) {
        // TODO: verify whether answer is List or array, see MultiChoiceQuestionBody
        choiceAnswers = ImmutableList.copyOf((List) result);
      } else {
        choiceAnswers = ImmutableList.of(result);
      }
    }
  }

  public static class NumericSurveyAnswer extends SurveyAnswer {

    private final Integer numericAnswer;

    public NumericSurveyAnswer(StepResult result) {
      super(result);
      numericAnswer = (Integer) result.getResult();
    }
  }

  public static class TextSurveyAnswer extends SurveyAnswer {

    private final String textAnswer;

    public TextSurveyAnswer(StepResult result) {
      super(result);
      textAnswer = (String) result.getResult();
    }
  }

  public static class DateSurveyAnswer extends SurveyAnswer {

    private final String dateAnswer;

    public DateSurveyAnswer(StepResult result) {
      super(result);
      Long dateResult = (Long) result.getResult();
      dateAnswer =
          dateResult == null ? null : FormatHelper.DEFAULT_FORMAT.format(new Date(dateResult));
    }
  }
}
