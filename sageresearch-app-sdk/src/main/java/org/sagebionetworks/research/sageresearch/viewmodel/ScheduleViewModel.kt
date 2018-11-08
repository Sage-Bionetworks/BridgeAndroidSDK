package org.sagebionetworks.research.sageresearch.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.support.annotation.VisibleForTesting
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.sagebionetworks.research.sageresearch.dao.room.ScheduleRepository
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId

import java.util.UUID

//
//  Copyright © 2018 Sage Bionetworks. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
// 1.  Redistributions of source code must retain the above copyright notice, this
// list of conditions and the following disclaimer.
//
// 2.  Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation and/or
// other materials provided with the distribution.
//
// 3.  Neither the name of the copyright holder(s) nor the names of any contributors
// may be used to endorse or promote products derived from this software without
// specific prior written permission. No license is granted to the trademarks of
// the copyright holders even if such marks are included in this software.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//

/**
 * Abstract base class for ScheduleViewModel that simply uses the application to create the dao
 */
abstract class ScheduleViewModel(private var scheduleDao: ScheduledActivityEntityDao,
        protected var scheduleRepo: ScheduleRepository) : ViewModel() {

    protected val compositeDispose = CompositeDisposable()

    val scheduleSyncErrorMessageLiveData = MutableLiveData<String>()

    @VisibleForTesting
    protected open val timezone: ZoneId get() = ZoneId.systemDefault()

    protected fun toInstant(dateTime: LocalDateTime): Instant {
        return dateTime.atZone(timezone).toInstant()
    }

    /**
     * Helper method to create an activity group available between query.
     * Because the scheduledOn and finishedOn vars are stored differently,
     * there is some conversion functionality we can include here to avoid duplicate work.
     * @param activityGroup the set of identifiers to filter on.
     * @param availableOnRange first is the start of the range, second is the end.
     * @return live data query to db
     */
    protected fun createActivityGroupAvailableBetween(
            activityGroup: Set<String>,
            availableOnRange: Pair<LocalDateTime, LocalDateTime>): LiveData<List<ScheduledActivityEntity>> {

        return scheduleDao.activityGroupAvailableBetween(activityGroup,
                availableOnRange.first, availableOnRange.second,
                toInstant(availableOnRange.first),
                toInstant(availableOnRange.second))
    }

    init {
        // This will make sure the schedules are synced with the server
        compositeDispose.add(
                scheduleRepo.syncSchedules().subscribe({
                    scheduleSyncErrorMessageLiveData.postValue(null)
                }, { t ->
                    scheduleSyncErrorMessageLiveData.postValue(t.localizedMessage)
                }))
    }

    /**
     * Runs a schedule using the task launcher, and informs the schedule repository about the
     * association between the taskRunUuid and the schedule guid so that it can be completed
     * @param scheduleGuid of the task to run
     * @return the uuid associated with this schedule
     */
    fun createScheduleTaskRunUuid(scheduleGuid: String?): UUID {
        scheduleGuid?.let {
            return scheduleRepo.createScheduleTaskRunUuid(it)
        } ?: return UUID.randomUUID()
    }

    /**
     * This only updates the schedule on bridge, specifically only the fields copied schedule.clientWritableCopy()
     * This function does not upload the result to S3
     * @param schedule to update on bridge
     */
    fun updateScheduleToBridge(schedule: ScheduledActivityEntity) {
        compositeDispose.add(
                scheduleRepo.updateScheduleToBridge(schedule).subscribe({
                    scheduleSyncErrorMessageLiveData.postValue(null)
                }, { t ->
                    scheduleSyncErrorMessageLiveData.postValue(t.localizedMessage)
                }))
    }

    /**
     * This should only be called with results of old research stack results
     * @param schedule to make the metadata info json archive, if null, none will be included
     * @param taskResult to upload to S3
     */
    fun uploadResearchStackTaskResultToS3(schedule: ScheduledActivityEntity?,
            taskResult: org.researchstack.backbone.result.TaskResult) {

        compositeDispose.add(
                scheduleRepo.uploadResearchStackTaskResultToS3(schedule, taskResult)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                    scheduleSyncErrorMessageLiveData.postValue(null)
                }, { t ->
                    scheduleSyncErrorMessageLiveData.postValue(t.localizedMessage)
                }))
    }

    /**
     * @return the study start date
     */
    fun studyStartDate(): DateTime? {
        return scheduleRepo.studyStartDate()
    }

    override fun onCleared() {
        super.onCleared()
        compositeDispose.clear()
    }
}