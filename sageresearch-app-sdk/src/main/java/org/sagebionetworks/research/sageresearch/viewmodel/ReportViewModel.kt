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

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.support.annotation.VisibleForTesting
import com.google.common.base.Preconditions
import org.sagebionetworks.bridge.android.BridgeConfig.ReportCategory.*
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntityDao
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.extensions.toInstant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import javax.inject.Inject

/**
 * The ReportViewModel is in charge of fetching and saving reports
 */
open class ReportViewModel(
        protected val reportDao: ReportEntityDao,
        protected val reportRepo: ReportRepository) : ViewModel() {

    class Factory @Inject constructor(private val reportDao: ReportEntityDao,
            private val reportRepo: ReportRepository) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            Preconditions.checkArgument(modelClass.isAssignableFrom(ReportViewModel::class.java))
            return ReportViewModel(reportDao, reportRepo) as T
        }
    }

    /**
     * Allow for customization of the timezone for unit testing purposes
     */
    @VisibleForTesting
    protected open val timezone: ZoneId get() = ZoneId.systemDefault()

    /**
     * @param reportIdentifier report returned will have this identifier
     * @return live data that will return the most recent report for the identifier, empty list if no reports found
     */
    fun mostRecentReport(reportIdentifier: String): LiveData<List<ReportEntity>> {
        return reportDao.mostRecentReport(reportIdentifier)
    }

    /**
     * @param reportIdentifier reports returned will all have this report identifier
     *                         if reportIdentifier maps to singleton type, epoch instant will be used as dates
     * @param start of the time window to query for reports,
     * @param end of the time window to query for reports
     * @return the reports with reportIdentifier and between start and end
     */
    fun reportsLiveData(reportIdentifier: String, start: LocalDateTime, end: LocalDateTime): LiveData<List<ReportEntity>> {
        return when(reportRepo.reportCategory(reportIdentifier)) {
            TIMESTAMP ->
                reportDao.reports(reportIdentifier,
                    start.toInstant(timezone), end.toInstant(timezone))
            GROUP_BY_DAY ->
                reportDao.reports(reportIdentifier,
                    start.toLocalDate(), end.toLocalDate())
            SINGLETON ->
                reportDao.reports(reportIdentifier,
                    reportRepo.reportSingletonLocalDate, reportRepo.reportSingletonLocalDate)
        }
    }

    /**
     * @param taskResult from a ResearchStack task it will be turned into reports and uploaded to bridge
     */
    fun saveResearchStackReports(taskResult: org.researchstack.backbone.result.TaskResult) {
        reportRepo.saveResearchStackReports(taskResult)
    }
}