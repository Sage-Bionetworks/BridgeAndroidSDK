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

package org.sagebionetworks.bridge.researchstack.step;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.collect.Sets;

import org.sagebionetworks.researchstack.backbone.answerformat.AnswerFormat;
import org.sagebionetworks.researchstack.backbone.result.TaskResult;
import org.sagebionetworks.researchstack.backbone.step.NavigationExpectedAnswerQuestionStep;
import org.sagebionetworks.researchstack.backbone.task.NavigableOrderedTask;
import org.sagebionetworks.bridge.android.manager.BridgeManagerProvider;
import org.sagebionetworks.bridge.researchstack.step.layout.DataGroupQuestionStepLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Data group question step, used to determine which data groups should apply to a user during
 * onboarding.
 */
public class DataGroupQuestionStep extends NavigationExpectedAnswerQuestionStep
        implements NavigableOrderedTask.NavigationSkipRule {
    private boolean shouldPersist = false;
    @NonNull
    private Set<String> skipOnSessionContainsAny = new HashSet<>();

    /**
     * Constructs the DataGroupQuestionStep
     *
     * @param identifier The identifier of the step (a step identifier should be unique within the
     *                   task).
     * @param title      A localized string that represents the primary text of the question.
     * @param format     The format in which the answer is expected.
     */
    public DataGroupQuestionStep(String identifier, String title, AnswerFormat format) {
        super(identifier, title, format);
    }

    /**
     * True if this should write all local data groups to Bridge Server. False if it should merely
     * save the question result to local storage. This is used if there are multiple questions
     * setting multiple data groups, so we make one server call instead of multiple.
     */
    public boolean shouldPersist() {
        return shouldPersist;
    }

    /** @see #shouldPersist */
    public void setShouldPersist(boolean shouldPersist) {
        this.shouldPersist = shouldPersist;
    }


    @NonNull
    public Set<String> getSkipOnSessionContainsAny() {
        return skipOnSessionContainsAny;
    }

    public void setSkipOnSessionContainsAny(@Nullable Set<String> skipOnSessionContainsAny) {
        this.skipOnSessionContainsAny = skipOnSessionContainsAny == null
                ? new HashSet<>() : skipOnSessionContainsAny;
    }

    /** {@inheritDoc} */
    @Override
    public Class getStepLayoutClass() {
        return DataGroupQuestionStepLayout.class;
    }

    @Override
    public boolean shouldSkipStep(@Nullable TaskResult result,
                                  @Nullable List<TaskResult> additionalTaskResults) {
        return !Sets.intersection(new HashSet<>(BridgeManagerProvider.getInstance()
                .getAuthenticationManager()
                .getUserSessionInfo()
                .getDataGroups()), skipOnSessionContainsAny).isEmpty();
    }
}
