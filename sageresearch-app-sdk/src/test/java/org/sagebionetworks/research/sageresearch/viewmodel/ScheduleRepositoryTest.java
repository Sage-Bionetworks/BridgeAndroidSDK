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

package org.sagebionetworks.research.sageresearch.viewmodel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static java.util.concurrent.TimeUnit.SECONDS;

import static hu.akarnokd.rxjava.interop.RxJavaInterop.toV1Single;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.android.manager.ActivityManager;
import org.sagebionetworks.bridge.android.manager.ParticipantRecordManager;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity;
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;

public class ScheduleRepositoryTest {

    @Rule
    public TestSchedulerRule testSchedulerRule = new TestSchedulerRule();

    @Mock
    private ActivityManager activityManager;

    @Mock
    private ParticipantRecordManager participantRecordManager;

    @Mock
    private ScheduledActivityEntityDao scheduledActivityEntityDao;

    @Mock
    private ScheduledRepositorySyncStateDao scheduledRepositorySyncStateDao;

    private ScheduleRepository scheduleRepository;

    @Before
    public void beforeTest() {
        MockitoAnnotations.initMocks(this);
        scheduleRepository = spy(
                new ScheduleRepository(scheduledActivityEntityDao, scheduledRepositorySyncStateDao, activityManager,
                        participantRecordManager));
    }

    @Test
    public void syncSchedules() {
        // TODO: 2018/10/05 test syncing
    }

    @Test
    public void syncFailedSchedules() {
        List<ScheduledActivityEntity> failedSchedules = mock(List.class);

        when(scheduledActivityEntityDao.activitiesThatNeedSyncedToBridge()).thenReturn(failedSchedules);

        doReturn(Completable.complete())
                .when(scheduleRepository).updateSchedulesToBridge(failedSchedules);

        scheduleRepository.syncFailedSchedules().test().assertComplete();

        InOrder inOrder = inOrder(scheduleRepository, scheduledActivityEntityDao);
        inOrder.verify(scheduledActivityEntityDao).activitiesThatNeedSyncedToBridge();
        inOrder.verify(scheduleRepository).updateSchedulesToBridge(failedSchedules);
    }

    @Test
    public void updateSchedulesToBridge() {
        ScheduledActivity scheduledActivity1 = mock(ScheduledActivity.class);
        ScheduledActivityEntity scheduledActivityEntity1 = mock(ScheduledActivityEntity.class);
        when(scheduledActivityEntity1.clientWritableCopy()).thenReturn(scheduledActivity1);

        ScheduledActivity scheduledActivity2 = mock(ScheduledActivity.class);
        ScheduledActivityEntity scheduledActivityEntity2 = mock(ScheduledActivityEntity.class);
        when(scheduledActivityEntity2.clientWritableCopy()).thenReturn(scheduledActivity2);

        when(activityManager.updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2))))
                .thenReturn(toV1Single(Single.just(new Message()).delay(1, SECONDS)));

        TestObserver testObserver = scheduleRepository.updateSchedulesToBridge(
                Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)).test();

        verify(activityManager).updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2)));

        InOrder inOrder = inOrder(scheduledActivityEntity1, scheduledActivityEntity2, scheduledActivityEntityDao);

        verify(scheduledActivityEntity2)
                .setNeedsSyncedToBridge(true);
        verify(scheduledActivityEntity1)
                .setNeedsSyncedToBridge(true);

        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)));
        inOrder.verifyNoMoreInteractions();

        testObserver.assertNotComplete();

        testSchedulerRule.getTestScheduler().advanceTimeBy(1, SECONDS);

        verify(scheduledActivityEntity2)
                .setNeedsSyncedToBridge(false);
        verify(scheduledActivityEntity1)
                .setNeedsSyncedToBridge(false);

        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)));
        inOrder.verifyNoMoreInteractions();

        testObserver.assertComplete();
    }

    @Test
    public void updateSchedulesToBridge_unrecoverable() {
        ScheduledActivity scheduledActivity1 = mock(ScheduledActivity.class);
        ScheduledActivityEntity scheduledActivityEntity1 = mock(ScheduledActivityEntity.class);
        when(scheduledActivityEntity1.clientWritableCopy()).thenReturn(scheduledActivity1);

        ScheduledActivity scheduledActivity2 = mock(ScheduledActivity.class);
        ScheduledActivityEntity scheduledActivityEntity2 = mock(ScheduledActivityEntity.class);
        when(scheduledActivityEntity2.clientWritableCopy()).thenReturn(scheduledActivity2);

        Single<Message> response = Single.<Message>error(new Throwable("Client data too large")).delay(1, SECONDS);
        when(activityManager.updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2))))
                .thenReturn(toV1Single(response));

        TestObserver testObserver = scheduleRepository.updateSchedulesToBridge(
                Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)).test();

        verify(activityManager).updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2)));

        InOrder inOrder = inOrder(scheduledActivityEntity1, scheduledActivityEntity2, scheduledActivityEntityDao);

        verify(scheduledActivityEntity2)
                .setNeedsSyncedToBridge(true);
        verify(scheduledActivityEntity1)
                .setNeedsSyncedToBridge(true);

        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)));
        inOrder.verifyNoMoreInteractions();

        testObserver.assertNotComplete();

        reset(scheduleRepository);
        doReturn(Completable.complete())
                .when(scheduleRepository).updateScheduleToBridge(any());

        testSchedulerRule.getTestScheduler().advanceTimeBy(1, SECONDS);

        verify(scheduleRepository).updateScheduleToBridge(scheduledActivityEntity1);
        verify(scheduleRepository).updateScheduleToBridge(scheduledActivityEntity2);
        verifyNoMoreInteractions(scheduleRepository);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void updateScheduleToBridge() {
        ScheduledActivity scheduledActivity = mock(ScheduledActivity.class);
        ScheduledActivityEntity scheduledActivityEntity = mock(ScheduledActivityEntity.class);
        when(scheduledActivityEntity.clientWritableCopy()).thenReturn(scheduledActivity);

        when(activityManager.updateActivities(eq(Collections.singletonList(scheduledActivity))))
                .thenReturn(toV1Single(Single.just(new Message()).delay(1, SECONDS)));

        TestObserver testObserver = scheduleRepository.updateScheduleToBridge(scheduledActivityEntity).test();

        verify(activityManager).updateActivities(eq(Collections.singletonList(scheduledActivity)));

        InOrder inOrder = inOrder(scheduledActivityEntity, scheduledActivityEntityDao);

        inOrder.verify(scheduledActivityEntity)
                .setNeedsSyncedToBridge(true);
        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Collections.singletonList(scheduledActivityEntity)));
        inOrder.verifyNoMoreInteractions();

        // expect
        testObserver.assertNotComplete();

        // return from the response
        testSchedulerRule.getTestScheduler().advanceTimeBy(1, SECONDS);

        inOrder.verify(scheduledActivityEntity)
                .setNeedsSyncedToBridge(false);
        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Collections.singletonList(scheduledActivityEntity)));
        inOrder.verifyNoMoreInteractions();

        testObserver.assertComplete();
    }

    @Test
    public void updateScheduleToBridge_unrecoverable() {
        ScheduledActivity scheduledActivity = mock(ScheduledActivity.class);
        ScheduledActivityEntity scheduledActivityEntity = mock(ScheduledActivityEntity.class);

        when(scheduledActivityEntity.clientWritableCopy()).thenReturn(scheduledActivity);

        when(activityManager.updateActivities(eq(Collections.singletonList(scheduledActivity))))
                .thenReturn(toV1Single(
                        Single.<Message>error(new Throwable("Client data too large")).delay(1, SECONDS)));

        TestObserver testObserver = scheduleRepository.updateScheduleToBridge(scheduledActivityEntity).test();

        verify(activityManager).updateActivities(eq(Collections.singletonList(scheduledActivity)));

        InOrder inOrder = inOrder(scheduledActivityEntity, scheduledActivityEntityDao);

        inOrder.verify(scheduledActivityEntity)
                .setNeedsSyncedToBridge(true);
        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Collections.singletonList(scheduledActivityEntity)));
        inOrder.verifyNoMoreInteractions();

        // expect
        testObserver.assertNotComplete();

        // return from the response
        testSchedulerRule.getTestScheduler().advanceTimeBy(1, SECONDS);

        inOrder.verify(scheduledActivityEntity)
                .setNeedsSyncedToBridge(false);
        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Collections.singletonList(scheduledActivityEntity)));
        inOrder.verifyNoMoreInteractions();

        testObserver.assertComplete();
    }

    @Test
    public void updateScheduleToBridge_recoverable() {
        ScheduledActivity scheduledActivity = mock(ScheduledActivity.class);
        ScheduledActivityEntity scheduledActivityEntity = mock(ScheduledActivityEntity.class);

        when(scheduledActivityEntity.clientWritableCopy()).thenReturn(scheduledActivity);

        when(activityManager.updateActivities(eq(Collections.singletonList(scheduledActivity))))
                .thenReturn(
                        toV1Single(
                                Single.<Message>error(new Throwable("Recoverable")).delay(1, SECONDS)));

        TestObserver testObserver = scheduleRepository.updateScheduleToBridge(scheduledActivityEntity).test();

        InOrder inOrder = inOrder(scheduledActivityEntity, scheduledActivityEntityDao);

        inOrder.verify(scheduledActivityEntity)
                .setNeedsSyncedToBridge(true);
        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Collections.singletonList(scheduledActivityEntity)));
        inOrder.verifyNoMoreInteractions();

        // expect
        testObserver.assertNotComplete();

        // return from the response
        testSchedulerRule.getTestScheduler().advanceTimeBy(1, SECONDS);

        verify(activityManager).updateActivities(eq(Collections.singletonList(scheduledActivity)));
        testObserver.assertComplete();
    }
}