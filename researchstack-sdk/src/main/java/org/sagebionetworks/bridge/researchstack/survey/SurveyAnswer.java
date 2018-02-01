package org.sagebionetworks.bridge.researchstack.survey;

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

  public static class ChoiceSurveyAnswer extends SurveyAnswer {
    public final List<?> choiceAnswers;

    public ChoiceSurveyAnswer(StepResult<?> stepResult) {
      super(stepResult);

      Object result = stepResult.getResult();
      if (result instanceof List) {
        choiceAnswers = ImmutableList.copyOf((List) result);
      } else if (result instanceof Object[]) {
        choiceAnswers = ImmutableList.copyOf((Object[]) result);
      } else if (result != null) {
        choiceAnswers = ImmutableList.of(result);
      } else {
        choiceAnswers = ImmutableList.of();
      }
    }
  }

  public static class NumericSurveyAnswer<T extends Number> extends SurveyAnswer {

    private final T numericAnswer;

    public NumericSurveyAnswer(StepResult result) {
      super(result);
      numericAnswer = (T) result.getResult();
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
    public String getDateAnswer() {
      return dateAnswer;
    }

    public DateSurveyAnswer(StepResult result) {
      super(result);
      Long dateResult = (Long) result.getResult();
      dateAnswer =
          dateResult == null ? null : FormatHelper.DEFAULT_FORMAT.format(new Date(dateResult));
    }
  }
}
