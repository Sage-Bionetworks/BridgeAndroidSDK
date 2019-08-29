package org.sagebionetworks.bridge.researchstack.factory;

import org.junit.Test;
import org.sagebionetworks.researchstack.backbone.answerformat.AnswerFormat;
import org.sagebionetworks.researchstack.backbone.answerformat.ChoiceAnswerFormat;
import org.sagebionetworks.researchstack.backbone.result.FileResult;
import org.sagebionetworks.researchstack.backbone.result.StepResult;
import org.sagebionetworks.bridge.data.ByteSourceArchiveFile;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.bridge.researchstack.survey.SurveyAnswer;
import org.sagebionetworks.bridge.rest.RestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by liujoshua on 11/10/2017.
 */
public class ArchiveFileFactoryTest {
    private ArchiveFileFactory archiveFileFactory = new ArchiveFileFactory();

    @Test
    public void toArchiveFile_forStepResult() throws Exception {
        StepResult<List<String>> stepResultMock = createStepResultMock(
                new ChoiceAnswerFormat(AnswerFormat.ChoiceAnswerStyle.SingleChoice)
        );

        List<String> results = Arrays.asList("stringResult");
        when(stepResultMock.getResult()).thenReturn(results);

        SurveyAnswer.ChoiceSurveyAnswer answer = createAnswer(stepResultMock,
                SurveyAnswer.ChoiceSurveyAnswer.class);

        // sanity check the result is wired through
        assertEquals(results, answer.choiceAnswers);

        JsonArchiveFile file = archiveFileFactory.fromStepResult(stepResultMock);

        String jsonString = file.getByteSource().asCharSource(StandardCharsets.UTF_8).read();

        SurveyAnswer.ChoiceSurveyAnswer deserializedAnswer =
                RestUtils.GSON.fromJson(jsonString, SurveyAnswer.ChoiceSurveyAnswer.class);

        assertEquals(answer.item, deserializedAnswer.item);
        assertEquals(answer.startDate, deserializedAnswer.startDate);
        assertEquals(answer.endDate, deserializedAnswer.endDate);
        assertEquals(answer.questionType, deserializedAnswer.questionType);
        assertEquals(answer.questionTypeName, deserializedAnswer.questionTypeName);
        assertEquals(answer.choiceAnswers, deserializedAnswer.choiceAnswers);
    }

    private StepResult createStepResultMock(AnswerFormat answerFormat) {
        StepResult mock = mock(StepResult.class);
        when(mock.getIdentifier()).thenReturn("id");
        when(mock.getAnswerFormat()).thenReturn(answerFormat);
        when(mock.getStartDate()).thenReturn(new Date());
        when(mock.getEndDate()).thenReturn(new Date());

        return mock;
    }

    private <T extends SurveyAnswer> T createAnswer(StepResult result, Class<T> klass) {
        SurveyAnswer rawAnswer = archiveFileFactory.surveyAnswer(result);
        assertThat(rawAnswer, instanceOf(klass));
        return (T) rawAnswer;
    }

    @Test
    public void toArchiveFile_forFileResultWithExtension() throws Exception {
        String identifier = "identifier";
        String contentType = "contentType";
        String filename = "data.csv";

        File file = mock(File.class);
        when(file.getName()).thenReturn(filename);
        when(file.canRead()).thenReturn(true);
        when(file.exists()).thenReturn(true);
        when(file.isFile()).thenReturn(true);

        FileResult fileResult = new FileResult(identifier, file, contentType);
        ByteSourceArchiveFile archiveFile = archiveFileFactory.fromFileResult(fileResult);

        assertEquals("identifier.csv", archiveFile.getFilename());
    }

    @Test
    public void toArchiveFile_forFileResultWithoutExtension() throws Exception {
        String identifier = "identifier";
        String contentType = "contentType";
        String filename = "data";

        File file = mock(File.class);
        when(file.getName()).thenReturn(filename);
        when(file.canRead()).thenReturn(true);
        when(file.exists()).thenReturn(true);
        when(file.isFile()).thenReturn(true);
        

        FileResult fileResult = new FileResult(identifier, file, contentType);
        ByteSourceArchiveFile archiveFile = archiveFileFactory.fromFileResult(fileResult);

        assertEquals("identifier.json", archiveFile.getFilename());
    }
}