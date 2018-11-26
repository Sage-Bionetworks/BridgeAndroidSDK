/*
 * BSD 3-Clause License
 *
 * Copyright 2018  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.research.sageresearch_app_sdk.archive;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.data.ArchiveFile;
import org.sagebionetworks.bridge.data.JsonArchiveFile;
import org.sagebionetworks.research.domain.result.interfaces.AnswerResult;
import org.sagebionetworks.research.domain.result.interfaces.Result;
import org.sagebionetworks.research.domain.result.interfaces.TaskResult;

import java.util.HashMap;
import java.util.Map;

public class TaskResultAnswerMapResultArchiveFactory extends TaskResultArchiveFactory {
    /**
     * By setting the json archive name without an extension,
     * the bridge upload schema can map fields using dot notation.
     *
     * For example, say our json looked like this...
     * {
     *     "a" = 1,
     *     "b" = 2,
     *     "c" = {
     *         "a" = 1
     *     }
     * }
     * Then we could set up these fields in the bridge upload schema...
     * answers.a
     * answers.b
     * answers.c.a
     */
    public static final String ANSWERS_JSON_FILENAME = "answers";

    public TaskResultAnswerMapResultArchiveFactory() {
        super();
    }

    @CallSuper
    @Override
    protected void addArchives(
            @NonNull ImmutableSet.Builder<ArchiveFile> builder,
            @NonNull final TaskResult taskResult) {

        super.addArchives(builder, taskResult);

        Map<String, Object> answerMap = new HashMap<>();
        for (Result result: taskResult.getStepHistory()) {
            if (result instanceof AnswerResult) {
                AnswerResult answerResult = (AnswerResult)result;
                answerMap.put(answerResult.getIdentifier(), answerResult.getAnswer());
            }
        }
        if (!answerMap.isEmpty()) {
            // If we need the archive to include start/end dates, we need to include them
            // as keys in the answer map, like "answers.[resultId]EndDate, etc.
            DateTime answersArchiveDate = DateTime.now();
            JsonArchiveFile answersArchive =
                    new JsonArchiveFile(ANSWERS_JSON_FILENAME,
                            answersArchiveDate,
                            answerMap);
            builder.add(answersArchive);
        }
    }
}
