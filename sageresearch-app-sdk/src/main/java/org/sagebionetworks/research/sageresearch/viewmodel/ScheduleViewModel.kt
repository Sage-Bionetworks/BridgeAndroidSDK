package org.sagebionetworks.research.sageresearch.viewmodel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import org.sagebionetworks.research.sageresearch.dao.room.ResearchDatabase
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
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
abstract class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ResearchDatabase.getInstance(app)
    @VisibleForTesting
    protected open fun scheduleDao() = db.scheduleDao()

    private val scheduleRepo = ScheduleRepository.getInstance(app)

    @VisibleForTesting
    protected open val timezone: ZoneId get() {
        return ZoneId.systemDefault()
    }

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

        return scheduleDao().activityGroupAvailableBetween(activityGroup,
                availableOnRange.first, availableOnRange.second,
                toInstant(availableOnRange.first),
                toInstant(availableOnRange.second))
    }

    init {
        // This will make sure the schedules are synced with the server
        ScheduleRepository.getInstance(app).syncSchedules()
    }

    /**
     * Runs a schedule using the task launcher, and informs the schedule repository about the
     * association between the taskRunUuid and the schedule guid so that it can be completed
     * @param schedule of the task to run
     * @return the uuid associated with this schedule
     */
    fun createScheduleTaskRunUuid(schedule: ScheduledActivityEntity?): UUID {
        schedule?.let {
            return scheduleRepo.createScheduleTaskRunUuid(schedule)
        } ?: return UUID.randomUUID()
    }
}