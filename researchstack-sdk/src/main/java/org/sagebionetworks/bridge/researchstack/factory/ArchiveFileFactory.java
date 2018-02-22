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

package org.sagebionetworks.bridge.researchstack.factory;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.google.common.io.Files;
import com.google.gson.reflect.TypeToken;

import org.joda.time.DateTime;
import org.researchstack.backbone.answerformat.AnswerFormat;
import org.researchstack.backbone.result.FileResult;
import org.researchstack.backbone.result.Result;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.TappingIntervalResult;
import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.bridge.data.ByteSourceArchiveFile;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.bridge.researchstack.survey.SurveyAnswer;
import org.sagebionetworks.bridge.rest.RestUtils;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates ArchiveFiles from ResearchStack Results.
 */
public class ArchiveFileFactory {

    private static final Type TYPE_OF_MAP = new TypeToken<Map<String, Object>>() {
    }.getType();

    public ArchiveFileFactory() {
    }

    /**
     * @param identifier identifier for the result
     * @return the filename to use for the bridge result
     */
    protected String getFilename(String identifier) {
        return identifier.replace(".", "_");
    }

    @Nullable
    public ArchiveFile fromResult(@NonNull Result result) {
        checkNotNull(result);
        checkNotNull(result.getIdentifier());
        checkNotNull(result.getEndDate());

        if (result instanceof StepResult) {
            if (((StepResult) result).getResult() == null) {
                return null; // Skipped StepResults will have a null result
            }
            return fromStepResult((StepResult) result);
        } else if (result instanceof FileResult) {
            return fromFileResult((FileResult) result);
        } else {
            if (result instanceof TappingIntervalResult) {
                DateTime endTime = new DateTime(result.getEndDate());

                String filename = getFilename(result.getIdentifier()) + ".json";
                String json = RestUtils.GSON.toJson(result, TappingIntervalResult.class);
                return new JsonArchiveFile(filename, endTime, json);
            }
        }
        return null;
    }

    @VisibleForTesting
    JsonArchiveFile fromStepResult(StepResult stepResult) {
        DateTime endTime = new DateTime(stepResult.getEndDate());

        String filename = getFilename(stepResult.getIdentifier()) + ".json";

        // If a step result has an answer format, we know that it was formed from a QuestionStep
        if (stepResult.getAnswerFormat() != null) {
            SurveyAnswer surveyAnswer = surveyAnswer(stepResult);
            return new JsonArchiveFile(filename, endTime, surveyAnswer);
        } else {  // otherwise make a generic String, Object JSON Map
            return new JsonArchiveFile(filename, endTime, stepResult.getResults(), TYPE_OF_MAP);
        }
    }

    /**
     * @param stepResult to transform into a survey answer
     * @param format the answer format that should be analyzed to make a survey answer
     * @return a valid SurveyAnswer, or null if conversion is unknown
     */
    public SurveyAnswer customSurveyAnswer(StepResult stepResult, AnswerFormat format) {
        return null; // to be implemented by subclass
    }

    public SurveyAnswer surveyAnswer(StepResult stepResult) {
        AnswerFormat format = stepResult.getAnswerFormat();
        if (!(format.getQuestionType() instanceof AnswerFormat.Type)) {
            return customSurveyAnswer(stepResult, format);
        }
        AnswerFormat.Type type = (AnswerFormat.Type) format.getQuestionType();
        SurveyAnswer answer;
        switch (type) {
            case SingleChoice:
            case MultipleChoice:
                answer = new SurveyAnswer.ChoiceSurveyAnswer(stepResult);
                break;
            case Integer:
            case Decimal:
                answer = new SurveyAnswer.NumericSurveyAnswer(stepResult);
                break;
            case Boolean:
                answer = new SurveyAnswer.BooleanSurveyAnswer(stepResult);
                break;
            case Text:
                answer = new SurveyAnswer.TextSurveyAnswer(stepResult);
                break;
            case ImageChoice:
                if (stepResult.getResult() instanceof Number) {
                    answer = new SurveyAnswer.NumericSurveyAnswer(stepResult);
                } else if (stepResult.getResult() instanceof String) {
                    answer = new SurveyAnswer.TextSurveyAnswer(stepResult);
                } else {
                    throw new RuntimeException("Cannot upload ImageChoice result to bridge");
                }
                break;
            case Date:
                answer = new SurveyAnswer.DateSurveyAnswer(stepResult);
                break;
            case TimeOfDay:
                // TODO: implement time of day only sending the hour/min/sec
                answer = new SurveyAnswer.DateSurveyAnswer(stepResult);
                break;
            case None:
            case Scale:
            case Eligibility:
            case DateAndTime:
            case TimeInterval:
            case Location:
            case Form:
            default:
                throw new RuntimeException("Cannot upload this question type to bridge");
        }
        return answer;
    }

    @VisibleForTesting
    ByteSourceArchiveFile fromFileResult(FileResult fileResult) {
        DateTime endTime = new DateTime(fileResult.getEndDate());

        File file = fileResult.getFile();

        int lastIndex = file.getName().lastIndexOf(".");
        String fileExtension = ".json";
        if (fileResult.getContentType().equals("video/mp4")) {
            fileExtension = ".mp4";
        }
        if (lastIndex >= 0) {
            fileExtension = file.getName().substring(lastIndex, file.getName().length());
        }
        String filename = getFilename(fileResult.getIdentifier()) + fileExtension;

        return new ByteSourceArchiveFile(
                filename,
                endTime,
                Files.asByteSource(file));
    }
}