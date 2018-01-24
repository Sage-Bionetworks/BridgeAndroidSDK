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

package org.sagebionetworks.bridge.researchstack.survey;

import android.support.annotation.VisibleForTesting;

import org.researchstack.backbone.model.Choice;
import org.researchstack.backbone.model.survey.QuestionSurveyItem;

import java.util.Set;

/**
 * Data group question item, used to determine which data groups should apply to a user during
 * onboarding.
 */
public class DataGroupQuestionSurveyItem  extends QuestionSurveyItem<Choice<String>> {
    /**
     * Custom type identifier.
     */
    public static final String CUSTOM_TYPE = "dataGroups.singleChoiceText";

    private boolean shouldPersist;
    private Set<String> skipOnSessionContainsAny;

    @Override
    @VisibleForTesting
    public void setCustomTypeValue(String value) {
        super.setCustomTypeValue(value);
    }

    /**
     * True if this should write all local data groups to Bridge Server. False if it should merely
     * save the question result to local storage. This is used if there are multiple questions
     * setting multiple data groups, so we make one server call instead of multiple.
     */
    public boolean shouldPersist() {
        return shouldPersist;
    }

    /**
     * @see #shouldPersist
     */
    public void setShouldPersist(boolean shouldPersist) {
        this.shouldPersist = shouldPersist;
    }

    /**
     * @return true if question should be skipped when session contains data groups
     */
    public Set<String> getSkipOnSessionContainsAny() {
        return skipOnSessionContainsAny;
    }

    public void setSkipOnSessionContainsAny(Set<String> skipOnSessionContainsAny) {
        this.skipOnSessionContainsAny = skipOnSessionContainsAny;
    }
}
