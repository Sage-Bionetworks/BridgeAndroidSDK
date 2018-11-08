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

package org.sagebionetworks.research.sageresearch.dao.room

import android.support.annotation.VisibleForTesting
import hu.akarnokd.rxjava.interop.RxJavaInterop.toV2Single
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.sagebionetworks.bridge.android.BridgeConfig
import org.sagebionetworks.bridge.android.BridgeConfig.ReportCategory
import org.sagebionetworks.bridge.android.BridgeConfig.ReportCategory.*
import org.sagebionetworks.bridge.android.manager.ParticipantRecordManager
import org.sagebionetworks.research.domain.Schema
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import org.sagebionetworks.research.sageresearch.extensions.clientDataAnswerMap
import org.sagebionetworks.research.sageresearch.extensions.toInstant
import org.sagebionetworks.research.sageresearch.extensions.toJodaLocalDate
import org.sagebionetworks.research.sageresearch.extensions.toThreeTenLocalDate
import org.sagebionetworks.research.sageresearch.extensions.toThreeTenLocalDateTime
import org.slf4j.LoggerFactory
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import java.util.Collections

/**
 * The ReportRepository is responsible for downloading the study's reports and saving them to the Room database
 */
open class ReportRepository constructor(
        protected val reportDao: ReportEntityDao,
        protected val participantManager: ParticipantRecordManager,
        protected val bridgeConfig: BridgeConfig) {

    private val logger = LoggerFactory.getLogger(ReportRepository::class.java)

    protected val compositeDispose = CompositeDisposable()
    /**
     * Subscribes to the completable using the CompositeDisposable
     * This is open for unit testing purposes to to run a blockingGet() instead of an asynchronous subscribe
     */
    @VisibleForTesting
    protected open fun subscribeCompletable(completable: Completable, successMsg: String, errorMsg: String) {
        compositeDispose.add(completable
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    logger.info(successMsg)
                }, {
                    logger.warn("$errorMsg ${it.localizedMessage}")
                }))
    }

    /**
     * @property reportPageSizeV4 the number of reports that will be returned from bridge per page on the V4 call
     */
    open val reportPageSizeV4: Int get() = 50

    /**
     * @property reportSingletonDate used when doing read/write on singleton category reports
     */
    val reportSingletonDate: Instant = Instant.EPOCH
    val reportSingletonJodaLocalDate = org.joda.time.LocalDate(reportSingletonDate.toEpochMilli(), DateTimeZone.UTC)
    val reportSingletonLocalDate = reportSingletonJodaLocalDate.toThreeTenLocalDate()

    /**
     * @property categoryMapping for report identifiers and which category they should be considered
     *                           the bridge config asset report_mapping.json takes precedence over this map
     */
    val categoryMapping: MutableMap<String, ReportCategory> = mutableMapOf()

    /**
     * @return the date the participant was created
     */
    open fun studyStartDate(): org.joda.time.DateTime? {
        return participantManager.participantCreatedOn
    }

    /**
     * Open for testing purposes
     * @return the current date and time
     */
    @VisibleForTesting
    protected open fun now(): org.joda.time.DateTime {
        return org.joda.time.DateTime.now()
    }

    /**
     * @return the timezone to allow for customization for unit testing
     */
    @VisibleForTesting
    protected open fun defaultTimeZone(): ZoneId {
        return ZoneId.systemDefault()
    }

    /**
     * @property asyncScheduler for performing network and database operations
     */
    @VisibleForTesting
    protected open val asyncScheduler: Scheduler get() = Schedulers.io()

    /**
     * This function may end up calling different APIs depending on what the ReportCategory is
     * that corresponds to the param reportIdentifier.
     * @param reportIdentifier only reports with this identifier will be fetched
     * @param start of the time window for grabbing reports
     * @param end of the time window for grabbing reports
     */
    fun fetchReports(reportIdentifier: String, start: LocalDateTime, end: LocalDateTime) {
        subscribeCompletable(fetchCompletable(reportIdentifier, start, end),
                "fetch reports succeeded", "fetch reports failed")
    }

    /**
     * Wrapper function for the mapping of the report category to the correct completable call
     * @param reportIdentifier only reports with this identifier will be fetched
     * @param start of the time window for grabbing reports
     * @param end of the time window for grabbing reports
     */
    protected fun fetchCompletable(reportIdentifier: String, start: LocalDateTime, end: LocalDateTime): Completable {
        return when (reportCategory(reportIdentifier)) {
            TIMESTAMP ->
                fetchAllReportsV4(reportIdentifier,
                        start.toInstant(defaultTimeZone()),
                        end.toInstant(defaultTimeZone()))
            GROUP_BY_DAY ->
                fetchReportsV3(reportIdentifier,
                        start.toLocalDate().toJodaLocalDate(),
                        end.toLocalDate().toJodaLocalDate())
            SINGLETON ->
                fetchReportsV3(reportIdentifier,
                        reportSingletonJodaLocalDate,
                        reportSingletonJodaLocalDate)
        }
    }

    /**
     * The V3 api returns all the reports in the time window.  They will also be grouped by day.
     * @param reportIdentifier only reports with this identifier will be fetched
     * @param start of the time window for grabbing reports
     * @param end of the time window for grabbing reports
     */
    protected fun fetchReportsV3(reportIdentifier: String,
            start: org.joda.time.LocalDate, end: org.joda.time.LocalDate): Completable {
        return toV2Single(participantManager
                .getReportsV3(reportIdentifier, start, end))
                .observeOn(asyncScheduler)
                .flatMapCompletable {
                    val reports = it.items.map { reportData ->
                        reportData.entityCopy(reportIdentifier)
                    }
                    replaceReportsInRoom(reportIdentifier,
                            start.toThreeTenLocalDate(), end.toThreeTenLocalDate(), reports)
                }
    }

    /**
     * The V4 api uses paging to return reports.  This gets all pages of reports in the time window.
     * @param reportIdentifier only reports with this identifier will be fetched
     * @param start of the time window for grabbing reports
     * @param end of the time window for grabbing reports
     */
    protected fun fetchAllReportsV4(reportIdentifier: String, start: Instant, end: Instant): Completable {
        val firstPage = FetchReportProgress(reportIdentifier, start, end, reportPageSizeV4)

        return getReportPageAndNextRecursive(firstPage)
                .observeOn(asyncScheduler)
                .concatMap {
                    logger.info("getReports concat " +
                            "with reports ${it.allReports.size} and next page ${it.nextOffsetPageKey}")
                    Observable.just(it)
                }
                .flatMapCompletable {
                    // This is called every page, so even if page 2/3 fails,
                    // page 1 reports will still save to the database correctly
                    // On the last page, the progress will contain the sum of all reports
                    replaceReportsInRoom(it.reportIdentifier, start, end, it.allReports)
                }
                .doOnError {
                    logger.warn("Failed to fetch reports from bridge")
                }
    }

    /**
     * Recursive function that fetches a single progress from bridge,
     * and then recursively fetches the next progress until all the pages have been fetched
     * @param progress current one to fetch
     * @return an observable for checking the state of each report
     */
    protected fun getReportPageAndNextRecursive(progress: FetchReportProgress): Observable<FetchReportProgress> {

        val pageOffsetKey = if (progress.isFirstPage) null else progress.nextOffsetPageKey
        val startDateTime =  DateTime(progress.start.toEpochMilli())
        val endDateTime = DateTime(progress.end.toEpochMilli())
        val reportSingle = toV2Single(participantManager.getReportsV4(
                progress.reportIdentifier, startDateTime, endDateTime, progress.pageSize, pageOffsetKey))

        return reportSingle.toObservable()
                .concatMap {
                    val reports = it.items.map { reportData ->
                        reportData.entityCopy(progress.reportIdentifier)
                    }
                    val newReports = listOf(progress.allReports, reports).flatMap { report -> report }
                    if (!it.isHasNext) {
                        logger.info("No next element found for report size ${it.items.size}")
                        Observable.just(progress.copy(allReports = newReports))
                    } else {
                        val progressCopy = progress.copy(
                                nextOffsetPageKey = it.nextPageOffsetKey, allReports = newReports)
                        logger.info(
                                "Next progress found with ${it.items.size} and progress offset ${it.nextPageOffsetKey}")
                        Observable.just(progressCopy).concatWith(getReportPageAndNextRecursive(progressCopy))
                    }
                }
    }

    /**
     * This function will first check if the most recent report is in the database.
     * If it is, no fetch from bridge is needed. If not, we need to query for all reports in the study duration.
     * @param reportIdentifier of the report
     */
    fun fetchMostRecentReportIfNotCached(reportIdentifier: String) {
        subscribeCompletable(
            Observable.just(reportDao)
                    .observeOn(asyncScheduler)
                    .concatMap {
                        if (it.mostRecentReportInternal(reportIdentifier).isEmpty()) {
                            val end = now().toThreeTenLocalDateTime()
                            val start = studyStartDate()?.toThreeTenLocalDateTime() ?: end
                            return@concatMap fetchCompletable(reportIdentifier, start, end)
                                    .toObservable<ReportEntityDao>()
                        }
                        Observable.just(it)
                    }
                    .flatMapCompletable {
                        Completable.complete()
                    }, "Fetch most recent finished", "Fetch most recent failed")
    }

    /**
     * Builds and saves the reports for the task result
     * @param taskResult to be analyzed and have reports made from its data
     */
    fun saveReports(taskResult: TaskResult) {
        val reports = buildReports(taskResult) ?:
            return // Exit early if there are no reports for this task.
        saveReports(reports)
    }

    /**
     * Builds and saves the reports for the task result
     * @param taskResult to be analyzed and have reports made from its data
     */
    fun saveResearchStackReports(taskResult: org.researchstack.backbone.result.TaskResult) {
        val reports = buildResearchStackReports(taskResult) ?:
        return // Exit early if there are no reports for this task.
        saveReports(reports)
    }

    /**
     * @param reports that will be saved to the database and added on bridge
     */
    protected fun saveReports(reports: List<ReportEntity>) {
        reports.forEach { it ->
            // Let's add a subscription for each one so that we know which reports synced with bridge properly
            subscribeCompletable(
                    toV2Single(participantManager
                    .saveReportJSON(it.identifier, it.bridgeCopy()))
                    .observeOn(asyncScheduler)
                    .flatMapCompletable { _ ->
                        it.needsSyncedToBridge = false
                        writeReportToRoom(it)
                    }
                    .onErrorResumeNext { throwable ->
                        // TODO: mdephillips 11/6/18 are there any errors that mean we shouldn't try to re-sync later?
                        logger.warn(throwable.localizedMessage)
                        it.needsSyncedToBridge = true
                        writeReportToRoom(it)
                    }, "Save report succeeded for $it", "Save report failed for $it")
        }
    }

    /**
     * @param taskResult to convert into reports
     * @return the reports to return for this task result.
     */
    open fun buildReports(taskResult: TaskResult): List<ReportEntity>? {
        // Recursively build a report for all the schemas in this task path.
        val newReports = ArrayList<ReportEntity>()
        val appendReportsFun: (taskResult: TaskResult) -> Unit = {
            val schemaInfo = schema(taskResult)
            val schemaIdentifier = schemaInfo?.identifier ?: taskResult.identifier
            val clientData = buildClientData(taskResult)
            val report = ReportEntity(identifier = schemaIdentifier, data = ClientData(clientData))
            setReportDate(report, taskResult.endTime)
            newReports.add(report)
        }
        taskResult.stepHistory.forEach { result ->
            (result as? TaskResult)?.let {
                appendReportsFun(it)
            }
        }
        appendReportsFun(taskResult)
        return if (newReports.isNotEmpty()) newReports else null
    }

    /**
     * @param taskResult to convert into reports
     * @return the reports to return for this task result.
     */
    open fun buildResearchStackReports(taskResult: org.researchstack.backbone.result.TaskResult): List<ReportEntity>? {
        // Recursively build a report for all the schemas in this task path.
        val schemaInfo = schema(taskResult.identifier)
        val schemaIdentifier = schemaInfo?.identifier ?: taskResult.identifier
        val clientData = buildClientData(taskResult)
        val report = ReportEntity(identifier = schemaIdentifier, data = ClientData(clientData))
        setReportDate(report, Instant.ofEpochMilli(taskResult.endDate.time))
        return listOf(report)
    }

    /**
     * @param taskResult to search for schema info within it or in the bridge config
     * @return the schema info associated with the given task result
     */
    open fun schema(taskResult: TaskResult): Schema? {
        if (taskResult.schemaInfo != null) {
            return taskResult.schemaInfo
        }
        return schema(taskResult.identifier)
    }

    /**
     * @param taskIdentifier to search for schema info in the bridge config
     * @return the schema info associated with the given task identifier
     */
    open fun schema(taskIdentifier: String): Schema? {
        bridgeConfig.taskToSchemaMap[taskIdentifier]?.let {
            return Schema(it.id, it.revision)
        }
        return null
    }

    /**
     * @return The date to use for the report with the given identifier.
     */
    open fun setReportDate(report: ReportEntity, resultEndTime: Instant?) {
        val reportIdentifier = report.identifier ?: return
        when (reportCategory(reportIdentifier)) {
            TIMESTAMP -> {
                report.dateTime = resultEndTime
            }
            GROUP_BY_DAY -> {
                report.localDate = resultEndTime?.atZone(defaultTimeZone())?.toLocalDate()
            }
            SINGLETON -> {
                report.localDate = reportSingletonLocalDate
            }
        }
    }

    /**
     * @return the report category (if any defined) for the given identifier. Default calls through to
     *         `BridgeConfiguration`, then the member var categoryMapping, then defaults to timestamp
     */
    open fun reportCategory(reportIdentifier: String): ReportCategory {
        bridgeConfig.taskToReportCategoryMap[reportIdentifier]?.let { return it }
        categoryMapping[reportIdentifier]?.let { return it }
        return TIMESTAMP
    }

    /**
     * Build the client data from the given task result.
     * @param taskResult: The task result for the task which has just run.
     * @return the client data built for this task result as a json string
     */
    open fun buildClientData(taskResult: TaskResult): Map<String, Any>? {
        return taskResult.clientDataAnswerMap()
    }

    /**
     * Build the client data from the given task result.
     * @param taskResult: The ResearchStack task result for the task which has just run.
     * @return the client data built for this task result as a json string
     */
    open fun buildClientData(rsTaskResult: org.researchstack.backbone.result.TaskResult): Map<String, Any>? {
        return rsTaskResult.clientDataAnswerMap()
    }

    /**
     * Encapsulate the db write operation in its own function for encapsulation into a completable
     */
    protected fun writeReportToRoom(report: ReportEntity): Completable {
        logger.info("cacheReports called for report: $report")
        return writeReportsToRoom(Collections.singletonList(report))
    }

    /**
     * Encapsulate the db write operation in its own function for encapsulation into a completable
     * @param reports to write to the database
     */
    protected fun writeReportsToRoom(reports: List<ReportEntity>): Completable {
        logger.info("cacheReports called for reports with size: ${reports.size}")

        return Completable.fromAction {
                    reportDao.upsert(reports)
                }
                .doOnError {
                    logger.warn(it.localizedMessage)
                }
    }

    /**
     * Delete query for the time range followed by an insert to replace the reports during this time
     * @param reportIdentifier only reports with this identifier will be fetched
     * @param start of the time window for replacing reports
     * @param end of the time window for replacing reports
     * @param reports that will replace all the ones in the delete query
     */
    protected fun replaceReportsInRoom(
            reportIdentifier: String,
            start: Instant,
            end: Instant,
            reports: List<ReportEntity>): Completable {

        return Completable.fromAction {
                reportDao.delete(reportIdentifier, start, end)
                reportDao.upsert(reports)
            }
            .doOnError {
                logger.warn(it.localizedMessage)
            }
    }

    /**
     * Delete query for the time range followed by an insert to replace the reports during this time
     * @param reportIdentifier only reports with this identifier will be fetched
     * @param start of the time window for replacing reports
     * @param end of the time window for replacing reports
     * @param reports that will replace all the ones in the delete query
     */
    protected fun replaceReportsInRoom(
            reportIdentifier: String,
            start: LocalDate,
            end: LocalDate,
            reports: List<ReportEntity>): Completable {

        return Completable.fromAction {
                reportDao.delete(reportIdentifier, start, end)
                reportDao.upsert(reports)
            }
            .doOnError {
                logger.warn(it.localizedMessage)
            }
    }

    /**
     * This is an internal protected data class used to wrap the concept of pages when passed
     * between the recursive function for building the observables to get all the report pages from bridge
     */
    protected data class FetchReportProgress(
            val reportIdentifier: String,
            val start: Instant,
            val end: Instant,
            val pageSize: Int,
            val nextOffsetPageKey: String = "start",
            val allReports: List<ReportEntity> = ArrayList()) {
        val isFirstPage: Boolean get() = (nextOffsetPageKey == "start")
    }
}