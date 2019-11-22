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

package org.sagebionetworks.bridge.researchstack.step.layout;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import android.util.AttributeSet;
import org.sagebionetworks.researchstack.backbone.DataProvider;
import org.sagebionetworks.researchstack.backbone.DataResponse;
import org.sagebionetworks.researchstack.backbone.ui.step.layout.SurveyStepLayout;
import org.sagebionetworks.researchstack.backbone.utils.StepLayoutHelper;
import org.sagebionetworks.bridge.researchstack.ApiUtils;
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider;
import org.sagebionetworks.bridge.researchstack.step.DataGroupQuestionStep;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import rx.Observable;

import java.util.List;

/**
 * Encapsulates the logic for the DataGroupQuestionStep, specifically storing the data group locally
 * and submitting it to the server if the shouldPersist flag is true.
 */
public class DataGroupQuestionStepLayout extends SurveyStepLayout {
    /** Required constructor */
    public DataGroupQuestionStepLayout(Context context) {
        super(context);
    }

    /** Required constructor */
    public DataGroupQuestionStepLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Required constructor */
    public DataGroupQuestionStepLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Called when the step is completed (not skipped) and the results are valid. This will store
     * the data group locally and submit to the server if the shouldPersist flag is true.
     */
    @Override
    protected void onComplete() {
        BridgeDataProvider dataProvider = (BridgeDataProvider) DataProvider.getInstance();

        // Save the data group locally. If specified.
        Object rawDataGroup = getRawDataGroupResult();
        if (rawDataGroup != null) {
            String dataGroup = rawDataGroup.toString();
            if (!dataGroup.isEmpty()) {
                dataProvider.addLocalDataGroup(dataGroup);
            }
        }

        // Persist data groups to server, if this step says we should.
        boolean shouldPersist = ((DataGroupQuestionStep) getStep()).shouldPersist();
        if (shouldPersist) {
            showLoadingDialog();

            // Update Participant API merges the participant we send up with the participant stored
            // in the server. Thus, it's safe to send a participant with just data groups.
            List<String> dataGroupList = dataProvider.getLocalDataGroups();
            StudyParticipant updatedParticipant = new StudyParticipant();
            updatedParticipant.setDataGroups(dataGroupList);
            Observable<DataResponse> updateObservable = dataProvider
                    .updateStudyParticipant(updatedParticipant).toCompletable()
                    .andThen(ApiUtils.SUCCESS_DATA_RESPONSE);

            StepLayoutHelper.safePerform(updateObservable, this,
                    new StepLayoutHelper.WebCallback() {
                        @Override
                        public void onSuccess(DataResponse response) {
                            hideLoadingDialog();
                            // Call super.onComplete() to handle the state transition.
                            superOnCompleteWrapper();
                        }

                        @Override
                        public void onFail(Throwable throwable) {
                            hideLoadingDialog();
                            showOkAlertDialog(throwable.getMessage());
                        }
                    });
        } else {
            // Call super.onComplete() to handle the state transition.
            superOnCompleteWrapper();
        }
    }

    @VisibleForTesting
    void superOnCompleteWrapper() {
        super.onComplete();
    }

    @VisibleForTesting
    Object getRawDataGroupResult() {
        return stepBody.getStepResult(false).getResult();
    }
}
