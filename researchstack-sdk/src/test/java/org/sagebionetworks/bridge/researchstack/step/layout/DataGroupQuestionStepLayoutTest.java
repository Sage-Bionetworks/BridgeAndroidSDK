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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import com.google.common.collect.Lists;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.researchstack.backbone.DataProvider;
import org.researchstack.backbone.DataResponse;
import org.researchstack.backbone.utils.StepLayoutHelper;
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider;
import org.sagebionetworks.bridge.researchstack.step.DataGroupQuestionStep;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.UserSessionInfo;

import java.util.List;

import rx.Completable;
import rx.Observable;

@PrepareForTest({Completable.class, StepLayoutHelper.class})
@RunWith(PowerMockRunner.class)
@SuppressWarnings("unchecked")
public class DataGroupQuestionStepLayoutTest {
    private static final String DATA_GROUP = "my_data_group";

    private BridgeDataProvider mockDataProvider;
    private DataGroupQuestionStep mockStep;
    private DataGroupQuestionStepLayout stepLayout;

    @Before
    public void setup() {
        // Use PowerMock to mock StepLayoutHelper
        mockStatic(StepLayoutHelper.class);

        // Mock DataProvider.
        mockDataProvider = mock(BridgeDataProvider.class);
        DataProvider.init(mockDataProvider);

        // Mock step
        mockStep = mock(DataGroupQuestionStep.class);

        // Step layouts are hairy to construct. Easier to just mock them and use callRealMethod().
        stepLayout = mock(DataGroupQuestionStepLayout.class);
        when(stepLayout.getStep()).thenReturn(mockStep);
    }

    @AfterClass
    public static void teardown() {
        // De-init DataProvider, so that it doesn't interfere with other tests.
        DataProvider.init(null);
    }

    @Test
    public void onComplete_NullDataGroup() {
        // Setup mocks for step and layout
        when(mockStep.shouldPersist()).thenReturn(false);

        when(stepLayout.getRawDataGroupResult()).thenReturn(null);
        doCallRealMethod().when(stepLayout).onComplete();

        // Execute
        stepLayout.onComplete();

        // Verify dependencies
        verify(mockDataProvider, never()).addLocalDataGroup(any());
        verify(mockDataProvider, never()).updateStudyParticipant(any());
        verify(stepLayout).superOnCompleteWrapper();

        verifyStatic(never());
        StepLayoutHelper.safePerform(any(), any(), any());
    }

    @Test
    public void onComplete_EmptyDataGroup() {
        // Setup mocks for step and layout
        when(mockStep.shouldPersist()).thenReturn(false);

        when(stepLayout.getRawDataGroupResult()).thenReturn("");
        doCallRealMethod().when(stepLayout).onComplete();

        // Execute
        stepLayout.onComplete();

        // Verify dependencies
        verify(mockDataProvider, never()).addLocalDataGroup(any());
        verify(mockDataProvider, never()).updateStudyParticipant(any());
        verify(stepLayout).superOnCompleteWrapper();

        verifyStatic(never());
        StepLayoutHelper.safePerform(any(), any(), any());
    }

    @Test
    public void onComplete_ShouldPersistFalse() {
        // Setup mocks for step and layout
        when(mockStep.shouldPersist()).thenReturn(false);

        when(stepLayout.getRawDataGroupResult()).thenReturn(DATA_GROUP);
        doCallRealMethod().when(stepLayout).onComplete();

        // Execute
        stepLayout.onComplete();

        // Verify dependencies
        verify(mockDataProvider).addLocalDataGroup(DATA_GROUP);
        verify(mockDataProvider, never()).updateStudyParticipant(any());
        verify(stepLayout).superOnCompleteWrapper();

        verifyStatic(never());
        StepLayoutHelper.safePerform(any(), any(), any());
    }

    @Test
    public void onComplete_ShouldPersistTrue() {
        // Setup mocks for step and layout
        when(mockStep.shouldPersist()).thenReturn(true);

        when(stepLayout.getRawDataGroupResult()).thenReturn(DATA_GROUP);
        doCallRealMethod().when(stepLayout).onComplete();

        // Mock data provider
        List<String> dataGroupList = Lists.newArrayList("foo", "bar", DATA_GROUP);
        when(mockDataProvider.getLocalDataGroups()).thenReturn(dataGroupList);

        Observable<DataResponse> mockDataResponseObservable = Observable.empty();

        // Need to use PowerMock to mock Completable.andThen() because it's final.
        Completable mockCompletable = PowerMockito.mock(Completable.class);
        PowerMockito.doReturn(mockDataResponseObservable).when(mockCompletable).andThen(
                any(Observable.class));

        Observable<UserSessionInfo> mockUpdateObservable = mock(Observable.class);
        when(mockUpdateObservable.toCompletable()).thenReturn(mockCompletable);
        when(mockDataProvider.updateStudyParticipant(any())).thenReturn(mockUpdateObservable);

        // Execute
        stepLayout.onComplete();

        // Verify dependencies
        verify(mockDataProvider).addLocalDataGroup(DATA_GROUP);

        ArgumentCaptor<StudyParticipant> participantCaptor = ArgumentCaptor.forClass(
                StudyParticipant.class);
        verify(mockDataProvider).updateStudyParticipant(participantCaptor.capture());
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(dataGroupList, participant.getDataGroups());

        verifyStatic();
        StepLayoutHelper.safePerform(same(mockDataResponseObservable), any(), any());

        // superOnCompleteWrapper() is called inside StepLayouHelper.safePerform(), which is mocked,
        // so the callback is never actually called.
    }
}
