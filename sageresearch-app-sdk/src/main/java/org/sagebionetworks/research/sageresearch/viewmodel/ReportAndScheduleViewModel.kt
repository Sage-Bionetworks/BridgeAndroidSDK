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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import com.google.common.base.Preconditions.checkArgument
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.dao.room.ScheduleRepository
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao
import org.slf4j.LoggerFactory
import javax.inject.Inject

/**
 * The ReportAndScheduleViewModel can be used to mediate changes in both schedules and reports together
 * Often times you want a schedule and its corresponding report (created from a TaskResult)
 * and you can easily coordinate those by using this ViewModel
 *
 * For instance, say you have a survey that is only scheduled once...
 * You could call the mediatorLiveData function with these parameters to get the schedule and it's report
 *
 *      mediatedLiveData(
 *          scheduleDao.activityGroup(setOf(SURVEY_IDENTIFIER)),
 *          mostRecentReport(SURVEY_IDENTIFIER))
 *
 * Your LiveData observer will get updates on init and anytime either the report or schedule changes.
 */
open class ReportAndScheduleViewModel(
        scheduleDao: ScheduledActivityEntityDao,
        scheduleRepo: ScheduleRepository,
        reportRepo: ReportRepository):
            ScheduleViewModel(scheduleDao, scheduleRepo),
            ReportViewModelInterface by ReportViewModelBaseImplementation(reportRepo) {

    private val logger = LoggerFactory.getLogger(ReportAndScheduleViewModel::class.java)

    class Factory @Inject constructor(
            val scheduleDao: ScheduledActivityEntityDao,
            val scheduleRepository: ScheduleRepository,
            val reportRepository: ReportRepository) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            checkArgument(modelClass.isAssignableFrom(ReportAndScheduleViewModel::class.java))
            return ReportAndScheduleViewModel(scheduleDao, scheduleRepository, reportRepository) as T
        }
    }

    /**
     * Fetches the schedules and reports requested and mediates the responses into a single data model
     * @return the live data which will post values when all queries return
     */
    fun mediatedLiveData(
            scheduleLiveData: LiveData<List<ScheduledActivityEntity>>,
            reportLiveData: LiveData<List<ReportEntity>>): LiveData<ReportAndScheduleModel> {
        return mediatedLiveData(listOf(scheduleLiveData), listOf(reportLiveData))
    }

    /**
     * Fetches the schedules and reports requested and mediates the responses into a single data model
     * @return the live data which will post values when all queries return
     */
    fun mediatedLiveData(
            scheduleLiveDataList: List<LiveData<List<ScheduledActivityEntity>>>,
            reportLiveDataList: List<LiveData<List<ReportEntity>>>): LiveData<ReportAndScheduleModel> {

        val mediator = MediatorLiveData<ReportAndScheduleModel>()

        scheduleLiveDataList.forEach { liveData ->
            mediator.addSource(liveData) { _ ->
                consolidateFromCurrentValues(scheduleLiveDataList, reportLiveDataList)?.let {
                    mediator.postValue(it)
                }
            }
        }

        reportLiveDataList.forEach { liveData ->
            mediator.addSource(liveData) { _ ->
                consolidateFromCurrentValues(scheduleLiveDataList, reportLiveDataList)?.let {
                    mediator.postValue(it)
                }
            }
        }

        return mediator
    }

    /**
     * @return the schedule and report live data only if both of them have values
     */
    private fun consolidateFromCurrentValues(
            scheduleLiveDataList: List<LiveData<List<ScheduledActivityEntity>>>,
            reportLiveDataList: List<LiveData<List<ReportEntity>>>): ReportAndScheduleModel? {

        val schedules = ArrayList<ScheduledActivityEntity>()
        val reports = ArrayList<ReportEntity>()
        scheduleLiveDataList.forEach {
            val scheduleValue = it.value ?: return null
            schedules.addAll(scheduleValue)
        }
        reportLiveDataList.forEach {
            val reportValue = it.value ?: return null
            reports.addAll(reportValue)
        }
        return ReportAndScheduleModel(schedules, reports)
    }
}

data class ReportAndScheduleModel(
        val schedules: List<ScheduledActivityEntity>,
        val reports: List<ReportEntity>)
