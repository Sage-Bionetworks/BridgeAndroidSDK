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
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.sagebionetworks.bridge.android.manager.ActivityManager
import org.sagebionetworks.bridge.android.manager.ParticipantRecordManager
import org.sagebionetworks.bridge.rest.model.Message
import org.sagebionetworks.bridge.rest.model.ScheduledActivity
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao
import org.threeten.bp.Instant
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

class ScheduleRepositoryTest {

    @Rule
    @JvmField
    public var testSchedulerRule = TestSchedulerRule()

    @Mock
    private lateinit var activityManager: ActivityManager

    @Mock
    private lateinit var participantRecordManager: ParticipantRecordManager

    @Mock
    private lateinit var scheduledActivityEntityDao: ScheduledActivityEntityDao

    @Mock
    private lateinit var scheduledRepositorySyncStateDao: ScheduledRepositorySyncStateDao

    private lateinit var scheduleRepository: ScheduleRepository

    @Before
    fun beforeTest() {
        MockitoAnnotations.initMocks(this)
        scheduleRepository = spy(
                ScheduleRepository(scheduledActivityEntityDao, scheduledRepositorySyncStateDao, activityManager,
                        participantRecordManager))
    }

    @Test
    fun syncSchedules() {
        // TODO: 2018/10/05 test syncing
    }

    @Test
    fun syncFailedSchedules() {
        val failedSchedules = mock<List<ScheduledActivityEntity>>()

        `when`(scheduledActivityEntityDao.activitiesThatNeedSyncedToBridge()).thenReturn(failedSchedules)

        doReturn(Completable.complete())
                .`when`<ScheduleRepository>(scheduleRepository).updateSchedulesToBridge(failedSchedules)

        scheduleRepository.syncFailedSchedules().test().assertComplete()

        val inOrder = inOrder(scheduleRepository, scheduledActivityEntityDao)
        inOrder.verify(scheduledActivityEntityDao).activitiesThatNeedSyncedToBridge()
        inOrder.verify<ScheduleRepository>(scheduleRepository).updateSchedulesToBridge(failedSchedules)
    }

    @Test
    fun updateSchedulesToBridge() {
        val scheduledActivity1 = mock<ScheduledActivity>()
        val scheduledActivityEntity1 = mock<ScheduledActivityEntity>()
        `when`(scheduledActivityEntity1.clientWritableCopy()).thenReturn(scheduledActivity1)

        val scheduledActivity2 = mock<ScheduledActivity>()
        val scheduledActivityEntity2 = mock<ScheduledActivityEntity>()
        `when`(scheduledActivityEntity2.clientWritableCopy()).thenReturn(scheduledActivity2)

        `when`(
                activityManager.updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2))))
                .thenReturn(toV1Single(Single.just(Message()).delay(1, SECONDS)))

        val testObserver = scheduleRepository.updateSchedulesToBridge(
                Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)).test()

        verify(activityManager).updateActivities(eq(Arrays.asList(scheduledActivity1, scheduledActivity2)))

        val inOrder = inOrder(scheduledActivityEntity1, scheduledActivityEntity2, scheduledActivityEntityDao)

        verify(scheduledActivityEntity2)
                .needsSyncedToBridge = true
        verify(scheduledActivityEntity1)
                .needsSyncedToBridge = true

        inOrder.verify(scheduledActivityEntityDao)
                .upsert(eq(Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)))
        inOrder.verifyNoMoreInteractions()

        testObserver.assertNotComplete()

        testSchedulerRule.testScheduler.advanceTimeBy(1, SECONDS)

        verify(scheduledActivityEntity2)
                .needsSyncedToBridge = false
        verify(scheduledActivityEntity1)
                .needsSyncedToBridge = false

        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)))
        inOrder.verifyNoMoreInteractions()

        testObserver.assertComplete()
    }

    @Test
    fun updateSchedulesToBridge_unrecoverable() {
        val scheduledActivity1 = mock<ScheduledActivity>()
        val scheduledActivityEntity1 = mock<ScheduledActivityEntity>()
        `when`(scheduledActivityEntity1.clientWritableCopy()).thenReturn(scheduledActivity1)

        val scheduledActivity2 = mock<ScheduledActivity>()
        val scheduledActivityEntity2 = mock<ScheduledActivityEntity>()
        `when`(scheduledActivityEntity2.clientWritableCopy()).thenReturn(scheduledActivity2)

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

        verify(scheduledActivityEntity2)
                .needsSyncedToBridge = true
        verify(scheduledActivityEntity1)
                .needsSyncedToBridge = true

        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(Arrays.asList(scheduledActivityEntity1, scheduledActivityEntity2)))
        inOrder.verifyNoMoreInteractions()

        testObserver.assertNotComplete()

        reset<ScheduleRepository>(scheduleRepository)
        doReturn(Completable.complete())
                .`when`<ScheduleRepository>(scheduleRepository).updateScheduleToBridge(any())

        testSchedulerRule.testScheduler.advanceTimeBy(1, SECONDS)

        verify<ScheduleRepository>(scheduleRepository).updateScheduleToBridge(scheduledActivityEntity1)
        verify<ScheduleRepository>(scheduleRepository).updateScheduleToBridge(scheduledActivityEntity2)
        verifyNoMoreInteractions(scheduleRepository)
        inOrder.verifyNoMoreInteractions()
    }

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

    @Test
    fun updateScheduleToBridge_unrecoverable() {
        val scheduledActivity = mock<ScheduledActivity>()
        val scheduledActivityEntity = mock<ScheduledActivityEntity>()

        `when`(scheduledActivityEntity.clientWritableCopy()).thenReturn(scheduledActivity)

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

        inOrder.verify(scheduledActivityEntity)
                .needsSyncedToBridge = false
        inOrder.verify<ScheduledActivityEntityDao>(scheduledActivityEntityDao)
                .upsert(eq(listOf(scheduledActivityEntity)))
        inOrder.verifyNoMoreInteractions()

        testObserver.assertComplete()
    }

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

    @Test
    fun scheduleUpdateFailed_noInternet() {

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

        val testObserver = scheduleRepository.updateSchedule(taskResult).test()

        inOrder(scheduledRepositorySyncStateDao, scheduledActivityEntity, scheduledActivityEntityDao) {
            verify(scheduledRepositorySyncStateDao).getScheduleGuid(taskRunUUID)
            verify(scheduledActivityEntityDao).activity(scheduleGuid)
            verify(scheduledActivityEntity).startedOn = taskStart
            verify(scheduledActivityEntity).finishedOn = taskEnd
            verify(scheduledActivityEntity).needsSyncedToBridge = true
            verify(scheduledActivityEntityDao).upsert(eq(Arrays.asList(scheduledActivityEntity)))
        }
        testObserver.assertNotComplete()

        reset(scheduledActivityEntityDao, scheduledActivityEntity)

        testSchedulerRule.testScheduler.advanceTimeBy(1, SECONDS)

        verifyNoMoreInteractions(scheduledActivityEntity, scheduledActivityEntityDao)

        testObserver.assertComplete()
    }
}