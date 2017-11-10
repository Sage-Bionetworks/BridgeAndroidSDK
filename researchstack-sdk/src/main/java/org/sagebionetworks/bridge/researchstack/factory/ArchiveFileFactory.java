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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.joda.time.DateTime;
import org.researchstack.backbone.result.FileResult;
import org.researchstack.backbone.result.Result;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.TappingIntervalResult;
import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.bridge.data.ByteSourceArchiveFile;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.bridge.researchstack.survey.SurveyAnswer;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates ArchiveFiles from ResearchStack Results.
 */
public class ArchiveFileFactory {
    /**
     * Singleton instance.
     */
    public static final ArchiveFileFactory INSTANCE = new ArchiveFileFactory();

    private static final Type TYPE_OF_MAP = new TypeToken<Map<String, Object>>() {
    }.getType();

    /**
     * Private constructor, to enforce the singleton property. This prevents creating additional
     * instances, but the factory can still be mocked.
     */
    private ArchiveFileFactory() {
    }

    /**
     * @param identifier identifier for the result
     * @return the filename to use for the bridge result
     */
    public String getFilename(String identifier) {
        return identifier.replace(".", "_");
    }

    @Nullable
    public ArchiveFile fromResult(@NonNull Result result) {
        checkNotNull(result);
        checkNotNull(result.getIdentifier());
        checkNotNull(result.getEndDate());

        if (result instanceof StepResult) {
            return fromStepResult((StepResult) result);
        } else if (result instanceof FileResult) {
            return fromFileResult((FileResult) result);
        } else {
            if (result instanceof TappingIntervalResult) {
                // TODO: replace this in RestUtils.GSON, see https://sagebionetworks.jira.com/browse/AA-59
                // TODO: you can do standard json parsing after this
                DateTime endTime = new DateTime(result.getEndDate());

                Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();
                String filename = getFilename(result.getIdentifier()) + ".json";
                String json = gson.toJson(result, TappingIntervalResult.class);
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
            SurveyAnswer surveyAnswer = SurveyAnswer.create(stepResult);

            return new JsonArchiveFile(filename, endTime, surveyAnswer);
        } else {  // otherwise make a generic String, Object JSON Map
            return new JsonArchiveFile(filename, endTime, stepResult.getResults(), TYPE_OF_MAP);
        }
    }

    @VisibleForTesting
    ByteSourceArchiveFile fromFileResult(FileResult fileResult) {
        DateTime endTime = new DateTime(fileResult.getEndDate());

        File file = fileResult.getFile();

        int lastIndex = file.getName().lastIndexOf(".");
        String fileExtension = ".json";
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