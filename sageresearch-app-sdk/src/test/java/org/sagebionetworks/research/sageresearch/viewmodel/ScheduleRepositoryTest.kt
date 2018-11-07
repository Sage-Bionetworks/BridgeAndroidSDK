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

package org.sagebionetworks.research.sageresearch.viewmodel

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import hu.akarnokd.rxjava.interop.RxJavaInterop.toV1Single
import io.reactivex.Completable
import io.reactivex.Single
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.sagebionetworks.bridge.android.BridgeConfig
import org.sagebionetworks.bridge.android.manager.ActivityManager
import org.sagebionetworks.bridge.android.manager.AuthenticationManager
import org.sagebionetworks.bridge.android.manager.ParticipantRecordManager
import org.sagebionetworks.bridge.android.manager.SurveyManager
import org.sagebionetworks.bridge.android.manager.UploadManager
import org.sagebionetworks.bridge.rest.model.Message
import org.sagebionetworks.bridge.rest.model.ScheduledActivity
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import org.sagebionetworks.research.sageresearch.dao.room.ScheduleRepository
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledRepositorySyncStateDao
import org.sagebionetworks.research.sageresearch.dao.room.clientWritableCopy
import org.threeten.bp.Instant
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

class ScheduleRepositoryTest {

    // TODO: mdephillips 10/19/18 Joshua Liu to fix at a future date

    @Rule
    @JvmField
    public var testSchedulerRule = TestSchedulerRule()

    @Mock
    private lateinit var activityManager: ActivityManager

    @Mock
    private lateinit var surveyManager: SurveyManager

    @Mock
    private lateinit var participantRecordManager: ParticipantRecordManager

    @Mock
    private lateinit var authManager: AuthenticationManager

    @Mock
    private lateinit var uploadManager: UploadManager

    @Mock
    private lateinit var bridgeConfig: BridgeConfig

    @Mock
    private lateinit var scheduledActivityEntityDao: ScheduledActivityEntityDao

    @Mock
    private lateinit var scheduledRepositorySyncStateDao: ScheduledRepositorySyncStateDao

    private lateinit var scheduleRepository: ScheduleRepository

    @Before
    fun beforeTest() {
        MockitoAnnotations.initMocks(this)
        scheduleRepository = spy(
                ScheduleRepository(scheduledActivityEntityDao,
                        scheduledRepositorySyncStateDao,
                        surveyManager, activityManager, participantRecordManager,
                        authManager, uploadManager, bridgeConfig))
    }

    @Ignore
    @Test
    fun syncSchedules() {
        // TODO: 2018/10/05 test syncing
    }

    @Ignore
    @Test
    fun syncFailedSchedules() {
        // setup
        val failedSchedules = mock<List<ScheduledActivityEntity>>()

        // DB returns failed schedules
        `when`(scheduledActivityEntityDao.activitiesThatNeedSyncedToBridge()).thenReturn(failedSchedules)

        // a successful sync to Bridge
        doReturn(Completable.complete())
                .`when`<ScheduleRepository>(scheduleRepository).updateSchedulesToBridge(failedSchedules)

        // perform action under test
        scheduleRepository.syncFailedSchedules().test().assertComplete()

        inOrder(scheduleRepository, scheduledActivityEntityDao) {
            // first we retrieve failed schedules
            verify(scheduledActivityEntityDao).activitiesThatNeedSyncedToBridge()
            // udate schedules should have been called on the schedules we just retrieved
            verify<ScheduleRepository>(scheduleRepository).updateSchedulesToBridge(failedSchedules)
        }
    }

    @Ignore
    @Test
    fun updateSchedulesToBridge() {
        // setup
        val scheduledActivity1 = mock<ScheduledActivity>()
        val scheduledActivityEntity1 = mock<ScheduledActivityEntity>()
        `when`(scheduledActivityEntity1.clientWritableCopy()).thenReturn(scheduledActivity1)

        val scheduledActivity2 = mock<ScheduledActivity>()
        val scheduledActivityEntity2 = mock<ScheduledActivityEntity>()
        `when`(scheduledActivityEntity2.clientWritableCopy()).thenReturn(scheduledActivity2)

        // successful update to Bridge
        `when`(
                activityManager.updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2))))
                .thenReturn(toV1Single(Single.just(Message()).delay(1, SECONDS)))

        // perform action under test
        val testObserver = scheduleRepository.updateSchedulesToBridge(
                Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)).test()

        verify(activityManager).updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2)))

        val inOrder = inOrder(scheduledActivityEntity1, scheduledActivityEntity2, scheduledActivityEntityDao)

        // after calling updateSchedulesToBridge, we should make the entities as stale and then persist them to DB
        verify(scheduledActivityEntity2)
                .needsSyncedToBridge = true
        verify(scheduledActivityEntity1)
                .needsSyncedToBridge = true

        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)))
        inOrder.verifyNoMoreInteractions()

        // not done yet, since we're still waiting for Bridge to respond
        testObserver.assertNotComplete()

        // proceed with response from Bridge
        testSchedulerRule.testScheduler.advanceTimeBy(1, SECONDS)

        // since we successfully synced, mark these entities as up-to-date and persist to DB
        verify(scheduledActivityEntity2)
                .needsSyncedToBridge = false
        verify(scheduledActivityEntity1)
                .needsSyncedToBridge = false

        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)))
        inOrder.verifyNoMoreInteractions()

        testObserver.assertComplete()
    }

    @Ignore
    @Test
    fun updateSchedulesToBridge_unrecoverableBatch() {
        val scheduledActivity1 = mock<ScheduledActivity>()
        val scheduledActivityEntity1 = mock<ScheduledActivityEntity>()
        `when`(scheduledActivityEntity1.clientWritableCopy()).thenReturn(scheduledActivity1)

        val scheduledActivity2 = mock<ScheduledActivity>()
        val scheduledActivityEntity2 = mock<ScheduledActivityEntity>()
        `when`(scheduledActivityEntity2.clientWritableCopy()).thenReturn(scheduledActivity2)

        // irrecoverable response from Bridge
        val response = Single.error<Message>(Throwable("Client data too large")).delay(1, SECONDS)
        `when`(
                activityManager.updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2))))
                .thenReturn(toV1Single(response))

        val testObserver = scheduleRepository.updateSchedulesToBridge(
                Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)).test()

        verify(activityManager) {
            activityManager.updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2)))
        }

        val inOrder = inOrder(scheduledActivityEntity1, scheduledActivityEntity2, scheduledActivityEntityDao)

        // mark all entities as stale as stale and save to DB
        verify(scheduledActivityEntity2)
                .needsSyncedToBridge = true
        verify(scheduledActivityEntity1)
                .needsSyncedToBridge = true
        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)))
        inOrder.verifyNoMoreInteractions()

        testObserver.assertNotComplete()

        // reset stubs and interactions on entities
        reset(scheduledActivityEntity1, scheduledActivityEntity2)
        // we should not be touching the entity's state in response to a batch update failure
        verifyNoMoreInteractions(scheduledActivity1, scheduledActivity2)

        // reset stubs and interactions on scheduleRepo
        reset<ScheduleRepository>(scheduleRepository)
        // expect updateScheduleToBridge to be called
        doReturn(Completable.complete())
                .`when`<ScheduleRepository>(scheduleRepository).updateScheduleToBridge(any())

        // now that failure has come back for the batch
        testSchedulerRule.testScheduler.advanceTimeBy(1, SECONDS)

        // we should get call updateScheduleToBridge once for each entity
        // in real practice, at least one of them should result in an unrecoverable failure
        verify<ScheduleRepository>(scheduleRepository).updateScheduleToBridge(scheduledActivityEntity1)
        verify<ScheduleRepository>(scheduleRepository).updateScheduleToBridge(scheduledActivityEntity2)
        verifyNoMoreInteractions(scheduleRepository)
        inOrder.verifyNoMoreInteractions()
    }

    @Ignore
    @Test
    fun updateScheduleToBridge() {
        val scheduledActivity = mock<ScheduledActivity>()
        val scheduledActivityEntity = mock<ScheduledActivityEntity>()
        `when`(scheduledActivityEntity.clientWritableCopy()).thenReturn(scheduledActivity)

        `when`(activityManager.updateActivities(eq(listOf(scheduledActivity))))
                .thenReturn(toV1Single(Single.just(Message()).delay(1, SECONDS)))

        val testObserver = scheduleRepository.updateScheduleToBridge(scheduledActivityEntity).test()

        verify(activityManager).updateActivities(eq(listOf(scheduledActivity)))

        val inOrder = inOrder(scheduledActivityEntity, scheduledActivityEntityDao)

        inOrder.verify(scheduledActivityEntity)
                .needsSyncedToBridge = true
        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(listOf(scheduledActivityEntity)))
        inOrder.verifyNoMoreInteractions()

        // expect
        testObserver.assertNotComplete()

        // return from the response
        testSchedulerRule.testScheduler.advanceTimeBy(1, SECONDS)

        inOrder.verify(scheduledActivityEntity)
                .needsSyncedToBridge = false
        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(listOf(scheduledActivityEntity)))
        inOrder.verifyNoMoreInteractions()

        testObserver.assertComplete()
    }

    @Ignore
    @Test
    fun updateScheduleToBridge_unrecoverable() {
        val scheduledActivity = mock<ScheduledActivity>()
        val scheduledActivityEntity = mock<ScheduledActivityEntity>()

        `when`(scheduledActivityEntity.clientWritableCopy()).thenReturn(scheduledActivity)

        // irrecoverable response from Bridge
        `when`(activityManager.updateActivities(eq(listOf(scheduledActivity))))
                .thenReturn(toV1Single(
                        Single.error<Message>(Throwable("Client data too large")).delay(1, SECONDS)))

        val testObserver = scheduleRepository.updateScheduleToBridge(scheduledActivityEntity).test()

        verify(activityManager).updateActivities(eq(listOf(scheduledActivity)))

        val inOrder = inOrder(scheduledActivityEntity, scheduledActivityEntityDao)

        inOrder.verify(scheduledActivityEntity)
                .needsSyncedToBridge = true
        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(listOf(scheduledActivityEntity)))
        inOrder.verifyNoMoreInteractions()

        // expect
        testObserver.assertNotComplete()

        // return from the response
        testSchedulerRule.testScheduler.advanceTimeBy(1, SECONDS)

        // mark entity as up-to-date (since future syncs to Bridge are deemed impossible) and persist to DB
        inOrder.verify(scheduledActivityEntity)
                .needsSyncedToBridge = false
        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(listOf(scheduledActivityEntity)))
        inOrder.verifyNoMoreInteractions()

        testObserver.assertComplete()
    }

    @Ignore
    @Test
    fun updateScheduleToBridge_recoverable() {
        val scheduledActivity = mock<ScheduledActivity>()
        val scheduledActivityEntity = mock<ScheduledActivityEntity>()

        `when`(scheduledActivityEntity.clientWritableCopy()).thenReturn(scheduledActivity)

        `when`(activityManager.updateActivities(eq(listOf(scheduledActivity))))
                .thenReturn(
                        toV1Single(
                                Single.error<Message>(Throwable("Recoverable")).delay(1, SECONDS)))

        val testObserver = scheduleRepository.updateScheduleToBridge(scheduledActivityEntity).test()

        var inOrder = inOrder(scheduledActivityEntity, scheduledActivityEntityDao)
        inOrder.verify(scheduledActivityEntity)
                .needsSyncedToBridge = true
        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(listOf(scheduledActivityEntity)))
        inOrder.verifyNoMoreInteractions()

        // expect
        testObserver.assertNotComplete()

        // return from the response
        testSchedulerRule.testScheduler.advanceTimeBy(1, SECONDS)

        verify(activityManager).updateActivities(eq(listOf(scheduledActivity)))
        testObserver.assertComplete()
    }

    @Ignore
    @Test
    fun scheduleUpdateFailed_noInternet() {
        // setup for test
        val taskStart = mock<Instant>()
        val taskEnd = mock<Instant>()
        val taskRunUUID = UUID.randomUUID()
        val taskResult = mock<TaskResult> {
            on { taskUUID }.doReturn(taskRunUUID)
            on { startTime }.doReturn(taskStart)
            on { endTime }.doReturn(taskEnd)
        }
        val scheduleGuid = "scheduleGuid"
        val scheduledActivityEntity = mock<ScheduledActivityEntity> {
            on { guid }.doReturn(scheduleGuid)
        }
        `when`(scheduledRepositorySyncStateDao.getScheduleGuid(taskRunUUID))
                .thenReturn(scheduleGuid)

        `when`(scheduledActivityEntityDao.activity(scheduleGuid))
                .thenReturn(Arrays.asList(scheduledActivityEntity))

        `when`(activityManager.updateActivities(any()))
                .thenReturn(toV1Single(Single.error<Message>(
                        Throwable("Unable to resolve host " +
                                "\"webservices.sagebase.org\", no address associated with hostname"))
                        .delay(1, SECONDS)))

        // perform action being tested
        val testObserver = scheduleRepository.updateSchedule(taskResult).test()

        // verify these actions occur after calling updateSchedule but before the response from Bridge
        inOrder(scheduledRepositorySyncStateDao, scheduledActivityEntity, scheduledActivityEntityDao) {
            // first to from taskRunUUID -> scheduleGuid
            verify(scheduledRepositorySyncStateDao).getScheduleGuid(taskRunUUID)
            // then go from scheduleGuid to entity
            verify(scheduledActivityEntityDao).activity(scheduleGuid)
            // we update the schedule using the task's start and end
            verify(scheduledActivityEntity).startedOn = taskStart
            verify(scheduledActivityEntity).finishedOn = taskEnd
            // before doing network call, mark entity as stale and persist our changes to the DB
            verify(scheduledActivityEntity).needsSyncedToBridge = true
            verify(scheduledActivityEntityDao).upsert(eq(Arrays.asList(scheduledActivityEntity)))
        }

        // we should not be done because the Bridge response is still pending
        testObserver.assertNotComplete()

        // resetting counts/stubs on these
        reset(scheduledActivityEntityDao, scheduledActivityEntity)

        // ok, now let's let the response come back
        testSchedulerRule.testScheduler.advanceTimeBy(1, SECONDS)

        // we don't need to change the state on the entity or the DB to retry the sync
        verifyNoMoreInteractions(scheduledActivityEntity, scheduledActivityEntityDao)

        testObserver.assertComplete()
    }
}