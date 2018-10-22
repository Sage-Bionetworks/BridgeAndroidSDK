package org.sagebionetworks.research.sageresearch.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.support.annotation.RestrictTo
import android.support.annotation.VisibleForTesting
import com.google.common.collect.ImmutableList
import hu.akarnokd.rxjava.interop.RxJavaInterop
import hu.akarnokd.rxjava.interop.RxJavaInterop.toV2Single
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.joda.time.Days
import org.sagebionetworks.bridge.android.BridgeConfig
import org.sagebionetworks.bridge.android.manager.ActivityManager
import org.sagebionetworks.bridge.android.manager.AuthenticationManager
import org.sagebionetworks.bridge.android.manager.ParticipantRecordManager
import org.sagebionetworks.bridge.android.manager.SurveyManager
import org.sagebionetworks.bridge.android.manager.UploadManager
import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4
import org.sagebionetworks.bridge.rest.model.Survey
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import org.sagebionetworks.research.sageresearch.dao.room.EntityTypeConverters
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao
import org.sagebionetworks.research.sageresearch.dao.room.clientWritableCopy
import org.sagebionetworks.research.sageresearch.extensions.isUnrecoverableError
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.CheckReturnValue
import javax.inject.Inject

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
 * The ScheduleRepository is responsible for downloading the study's schedules and saving them to the Room database
 * All @see ScheduleViewModel are required to call this class' function syncSchedules() to ensure that
 * the schedules are currently synced with bridge.
 */
open class ScheduleRepository constructor(
        private val scheduleDao: ScheduledActivityEntityDao,
        private val syncStateDao: ScheduledRepositorySyncStateDao,
        private val surveyManager: SurveyManager,
        private val activityManager: ActivityManager,
        private val participantRecordManager: ParticipantRecordManager,
        private val authenticationManager: AuthenticationManager,
        private val uploadManager: UploadManager,
        private val bridgeConfig: BridgeConfig) {

    private val logger = LoggerFactory.getLogger(ScheduleRepository::class.java)

    private val entityConverter = EntityTypeConverters()

    /**
     * @property isSyncing true if the study's schedules are currently being fetched from bridge,
     *                     false if no syncing is currently occurring.
     */
    private val isSyncing = AtomicBoolean(false)
    /**
     * @property isSynced true if the study's schedules have been successfully fetched from bridge,
     *                    false if it still need done.
     */
    private val isSynced = AtomicBoolean(false)

    // TODO: mdephillips 9/2/18 read this from bridge config
    /**
     * The number of days ahead we request from the server to allow for offline operation
     */
    open var cachedDaysAhead: Int = 7

    /**
     * The maximum days of schedules we can request at one time
     * ridge only allows requesting activity data in 14 day windows,
     * so we must break the request up into 14 day sections if necessary
     */
    val maxRequestDays: Int = 14

    /**
     * @property scheduleTaskRunUuidMap maps taskRunUuid to schedule guid so that we cannot find
     *                                  the correct schedule to update after a task completes
     */
    private val scheduleTaskRunUuidMap = HashMap<UUID, String>()

    /**
     * @property researchStackUploadArchiveFactory is only used when uploading ResearchStack results to S3
     *                                             set to provide custom archive functionality
     */
    var researchStackUploadArchiveFactory = ResearchStackUploadArchiveFactory()

    @VisibleForTesting
    protected open fun studyStartDate(): DateTime? {
        return participantRecordManager.participantCreatedOn
    }

    /**
     * Open for testing purposes
     * @return the current date and time
     */
    @VisibleForTesting
    protected open fun now(): DateTime {
        return DateTime.now()
    }

    /**
     * @property syncStartDate the start date time for the request to bridge to sync schedules
     */
    open val syncStartDate: DateTime?
        get() {
            return syncStateDao.lastQueryEndDate?.withTimeAtStartOfDay() ?: run {
                studyStartDate()?.withTimeAtStartOfDay()
            }
        }

    /**
     * @property syncEndDate the end date time for the request to bridge to sync schedules
     */
    open val syncEndDate: DateTime
        get() {
            return now().plusDays(cachedDaysAhead)
        }

    /**
     * Makes sure the schedules we have cached are synced with the bridge server.
     * This function only operates if we are not currently syncing or have successfully synced
     */
    @CheckReturnValue
    fun syncSchedules(): Completable {
        if (isSynced.get() || isSyncing.get()) {
            // TODO @liujoshua 2018/10/03 should we return active completable so caller can wait on real sync?
            return Completable.complete() // we are already retrieving the study's schedules
        }

        isSyncing.set(true)

        val startDate = syncStartDate ?: return Completable.complete() // return if we aren't signed in yet
        val endDate = syncEndDate
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(startDate, endDate, maxRequestDays)

        // Use these atomic variables to track of the state of the requests
        val requestHasFailed = AtomicBoolean(false)
        val requestCounter = AtomicInteger(requestMap.keys.size)

        // Run all the requests, and join the results for the user

        return Observable.fromIterable(requestMap.keys)
                .filter { requestMap[it] != null }
                .flatMapSingle {
                    RxJavaInterop.toV2Single(activityManager.getActivities(it, requestMap[it]!!))
                }
                .flatMapCompletable {
                    // If another request previously failed we won't trigger any success callbacks
                    if (!requestHasFailed.get()) {
                        requestCounter.set(requestCounter.get() - 1)
                        if (requestCounter.get() <= 0) {
                            syncStateDao.lastQueryEndDate = endDate
                            isSynced.set(true)
                            isSyncing.set(false)
                        }
                    }
                    // No matter if other requests failed, still cache a successful request
                    reconcileAndCacheSchedules(it)
                }
                .doOnError {
                    // Consider the sync a failure if one or more requests fail
                    if (!requestHasFailed.get()) {
                        isSynced.set(false)
                        isSyncing.set(false)
                        requestHasFailed.set(true)
                    }
                    logger.warn("Sync failed", it)
                }
    }

    /**
     * When a schedule fails to update to Bridge for whatever reason, it will be marked in the db as so.
     * This function queries the db for those schedules and re-attempts to update them on Bridge.
     */
    @CheckReturnValue
    fun syncFailedSchedules(): Completable {
        return Single.fromCallable{scheduleDao.activitiesThatNeedSyncedToBridge()}
                .subscribeOn(Schedulers.io())
                .flatMapCompletable { updateSchedulesToBridge(it) }
                .doOnError { logger.warn(it.localizedMessage) }
    }

    /**
     * Creates and associates the taskRunUuid and the schedule guid so that it can accessed later when complete
     * @param schedule of the task
     * @return the new associated uuid with this schedule
     */
    fun createScheduleTaskRunUuid(schedule: ScheduledActivityEntity): UUID {
        val uuid = UUID.randomUUID()
        syncStateDao.setScheduleGuid(uuid, schedule.guid)
        return uuid
    }

    /**
     * @param schedule that we are going to update on bridge
     *                 schedule can be null if the user does a task without internet
     * @param taskResult the result of the user doing the schedule task
     */
    @CheckReturnValue
    fun updateSchedule(taskResult: TaskResult): Completable {
        logger.debug("Updating schedule for task: {}", taskResult.taskUUID)
        // If we previously registered a guid with a taskRunUuid, we should be able to find the schedule in the
        // the database at this point based on the schedule guid

        return findSchedule(taskResult.taskUUID)
                .map(fun(schedule: ScheduledActivityEntity): ScheduledActivityEntity {
                    schedule.startedOn = taskResult.startTime
                    schedule.finishedOn = taskResult.endTime
                    return schedule
                })
                .flatMapCompletable { schedule ->
                    updateSchedulesToBridge(Collections.singletonList(schedule))
                }
                .doOnError { it.localizedMessage }
    }

    /**
     * Private function should only be accessed through this class as a re-usable way to update
     * schedules on bridge.
     */
    @CheckReturnValue
    open fun updateSchedulesToBridge(schedules: List<ScheduledActivityEntity>): Completable {
        val bridgeSchedules = schedules.map { it.clientWritableCopy() }

        schedules.forEach {
            it.needsSyncedToBridge = true
        }

        return cacheSchedules(schedules)
                .andThen(toV2Single(activityManager
                        .updateActivities(bridgeSchedules))
                        .flatMapCompletable {
                            schedules.forEach {
                                it.needsSyncedToBridge = false
                            }
                            cacheSchedules(schedules)
                        }.onErrorResumeNext { throwable ->
                            if (throwable.isUnrecoverableError()) {
                                // we have multiple schedules which failed, try to update them individually to identify which
                                // one(s) are unrecoverable
                                Completable.mergeDelayError(schedules.map {
                                    updateScheduleToBridge(it)
                                })
                            } else {
                                Completable.complete()
                            }
                        }
                )
    }

    @CheckReturnValue
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun updateScheduleToBridge(schedule: ScheduledActivityEntity): Completable {
        val bridgeSchedule = schedule.clientWritableCopy()

        schedule.needsSyncedToBridge = true

        return cacheSchedule(schedule)
                .andThen(
                        RxJavaInterop.toV2Single(
                                activityManager
                                        .updateActivities(listOf(bridgeSchedule))
                        ).flatMapCompletable {
                            schedule.needsSyncedToBridge = false
                            cacheSchedule(schedule)
                        }.onErrorResumeNext { throwable ->
                            if (throwable.isUnrecoverableError()) {
                                // There are some responses from the server that mean this call
                                // will never succeed, like a 400 client data too large error.
                                // If so, do not add the activity again to update.
                                logger.warn("Unrecoverable error, disabling future sync for schedule with guid: {}",
                                        schedule.guid, throwable)
                                schedule.needsSyncedToBridge = false
                                cacheSchedule(schedule)
                            } else {
                                Completable.complete()
                            }
                        }
                )
    }

    /**
     * Encapsulate db call functionality into it own function for providing custom mock behavior in tests,
     * and also to remove redundant threading code throughout the class.
     */
    @CheckReturnValue
    internal fun findSchedule(taskRunUuid: UUID): Single<ScheduledActivityEntity> {
        logger.debug("findScheduled called for taskRunUuid: {}", taskRunUuid)

        // Null schedule usually means that the developer forgot to call createScheduleTaskRunUuid before launching
        // the task associated with the schedule.
        // Or the user is offline and did a task without having synced the study's schedules.
        // Which, that is okay, the s3 upload will still take place, but we won't be able to update the schedule on bridge.
        // Side effects for this will be, no study reports or finishedOn status for the task will show in the UI.
        // TODO: mdephillips 9/14/2018 message the user there will be no history?
        val guid = syncStateDao.getScheduleGuid(taskRunUuid)
        return if (guid == null) {
            Single.error(Throwable("No schedule guid found for taskRunUuid $taskRunUuid, " +
                    "are you sure you function createScheduleTaskRunUuid() before running the task?"))
        } else {
            Single.fromCallable {
                scheduleDao.activity(guid).first() // NoSuchElementException
            }
                    .subscribeOn(Schedulers.io())
                    .onErrorResumeNext {
                        Single.error<ScheduledActivityEntity>(Throwable("No schedule found in DB with guid $guid"))
                    }
        }
    }

    /**
     * @param schedules that failed to update on bridge.
     * @param throwable the reason the schedule failed to update on bridge.
     */
    @CheckReturnValue
    private fun scheduleUpdateFailed(schedule: ScheduledActivityEntity, throwable: Throwable?): Completable {
        throwable?.let { t ->
            // There are some responses from the server that mean this call
            // will never succeed, like a 400 client data too large error.
            // If so, do not add the activity again to update.
            schedule.needsSyncedToBridge = !t.isUnrecoverableError()
            logger.warn("Unrecoverable error when syncing to Bridge, disabling future sync attempts", t)
        }
                ?: run {
                    schedule.needsSyncedToBridge = true
                }
        return cacheSchedules(listOf(schedule))
    }

    /**
     * @param schedule to cache in the db, simply a helper function to convert to list
     */
    @CheckReturnValue
    private fun cacheSchedule(schedule: ScheduledActivityEntity): Completable {
        logger.debug("cacheSchedule called for schedule with guid: {}", schedule.guid)
        return cacheSchedules(listOf(schedule))
    }

    /**
     * @param activityListV4 to convert to the entity format and cache
     */
    @CheckReturnValue
    private fun reconcileAndCacheSchedules(activityListV4: ScheduledActivityListV4): Completable {
        entityConverter.fromScheduledActivityListV4(activityListV4)?.let { list ->
            if (!reconcileWithDictionary()) {
                return cacheSchedules(list)
            }
        }
        return Completable.complete()
    }

    /**
     * Encapsulate the db write operation in its own function for providing custom mock behavior in tests,
     * and also to remove redundant threading code throughout the class.
     */
    @CheckReturnValue
    internal fun cacheSchedules(schedules: List<ScheduledActivityEntity>): Completable {
        logger.debug("cacheSchedules called for schedule guids: {}", schedules.map { it.guid })

        return Completable.fromAction {
            scheduleDao.upsert(schedules)
        }
                .subscribeOn(Schedulers.io())
                .doOnError {
                    logger.warn(it.localizedMessage)
                }
    }

    // TODO: mdephillips 9/2/18 correctly reconcile the schedules instead of doing an upsert
    fun reconcileWithDictionary(): Boolean {
        return false
        // For all but the client-writable fields, the server value is completely canonical.
        // For the client-writable fields, the client value is canonical unless it is nil.
        // @see ScheduleActivityEntity.clientWritableCopy()
    }

    /**
     * Loads a ResearchStack survey from bridge
     * @param surveyGuid of the survey
     * @param surveyCreatedOn of the survey, if null, newest published survey will be fetched
     */
    fun loadResearchStackSurvey(surveySchedule: ScheduledActivityEntity): Single<Survey> {
        surveySchedule.activity?.survey?.let {
            return toV2Single(surveyManager.getSurvey(it.guid, null))
        } ?: run {
            return Single.error(IllegalArgumentException("Schedule is not a survey"))
        }
    }

    /**
     * Uploads the ResearchStack task result to S3 with corresponding metadata
     * @param schedule for creating the metadata json file in the archive
     * @param taskResult for creating the answers map
     */
    fun uploadResearchStackTaskResultToS3(schedule: ScheduledActivityEntity?,
            taskResult: org.researchstack.backbone.result.TaskResult): Completable {

        // Attempt to build the archive to upload, if successful, upload to bridge
        researchStackUploadArchiveFactory.buildResearchStackArchive(
                schedule, bridgeConfig, userDataGroups(), userExternalId(), taskResult)?.let {

            return Completable.fromAction {
                uploadManager.queueUpload(it.first, it.second.build())
                        .flatMapCompletable {
                            uploadFile -> uploadManager.processUploadFile(uploadFile)
                        }.await()
            }

        } ?: return Completable.error(Exception("Failed to build upload archive"))
    }

    /**
     * @return the list of the users data groups,
     *         an empty list if the user is not signed in or has no data groups
     */
    protected fun userExternalId(): String? {
        return authenticationManager.userSessionInfo?.externalId
    }

    /**
     * @return the list of the users data groups,
     *         an empty list if the user is not signed in or has no data groups
     */
    protected fun userDataGroups(): ImmutableList<String> {
        return ImmutableList.copyOf(
                authenticationManager.userSessionInfo?.dataGroups ?: ArrayList())
    }
}

object ScheduleRepositoryHelper {
    /**
     * Builds a map of requests to make to the server that is appropriately grouped to not exceed the maxRequestDays.
     * The map will have requests in whole days only so that it is easier to avoid the concern of exceeding max.
     * The map is built in reverse order, so that requests favor getting today's new data first.
     * @param start of the request
     * @param end of the request
     * @param maxRequestDays the maximum number of days that can be requested
     */
    fun buildRequestMap(start: DateTime, end: DateTime, maxRequestDays: Int): Map<DateTime, DateTime> {
        // A LinkedHashMap will ensure that the requests are made in the order that we add key/values
        val requestMap = LinkedHashMap<DateTime, DateTime>()

        val startOfDay = start.withTimeAtStartOfDay()
        val endOfDay = end.withTimeAtStartOfDay().plusDays(1)
        var endDateCounter = DateTime(endOfDay)
        var durationCounter = Days.daysBetween(startOfDay, endOfDay).days

        // Build the map in reverse order, so that requests favor getting today's new data first
        while (durationCounter > maxRequestDays) {
            val requestStartDate = endDateCounter.minusDays(maxRequestDays)

            // We subtract 1 millisecond so that the schedules returned from bridge
            // do not return schedules for the next day if scheduled at midnight inclusively
            requestMap[requestStartDate] = endDateCounter.minusMillis(1)
            endDateCounter = endDateCounter.minusDays(maxRequestDays)
            durationCounter -= maxRequestDays
        }
        requestMap[startOfDay] = endDateCounter.minusMillis(1)

        return requestMap
    }
}

open class ScheduledRepositorySyncStateDao @Inject constructor(context: Context) {
    val logger = LoggerFactory.getLogger(ScheduledRepositorySyncStateDao::class.java)

    private val lastQueryDateKey = "lastQueryEndDate"

    /**
     * @property scheduleTaskRunUuidMap maps taskRunUuid to schedule guid so that we cannot find
     *                                  the correct schedule to update after a task completes
     */
    private val scheduleTaskRunUuidMap = HashMap<UUID, String>()

    /**
     * Used to store helpful data about the state of the ScheduleRepository
     */
    @VisibleForTesting
    var prefs: SharedPreferences =
            context.getSharedPreferences("ScheduleRepository", Context.MODE_PRIVATE)
        private set

    fun getScheduleGuid(taskRunUuid: UUID): String? {
        logger.debug("getScheduleGuid called for taskRunUUID: {}", taskRunUuid)
        return scheduleTaskRunUuidMap[taskRunUuid]
    }

    fun setScheduleGuid(taskRunUuid: UUID, scheduleGuid: String) {
        logger.debug("setScheduleGuid called for taskRunUUID: {}, scheduleGuid: {}", taskRunUuid, scheduleGuid)

        scheduleTaskRunUuidMap[taskRunUuid] = scheduleGuid
    }

    open var lastQueryEndDate: DateTime?
        get() {
            return prefs.getString(lastQueryDateKey, null)?.let {
                DateTime.parse(it)
            }
        }
        // Suppress any warnings because we need this operation to take place immediately
        @SuppressLint("ApplySharedPref")
        set(value) {
            value?.let {
                prefs.edit().putString(lastQueryDateKey, it.toString()).commit()
            } ?: run {
                prefs.edit().remove(lastQueryDateKey).commit()
            }
        }
}