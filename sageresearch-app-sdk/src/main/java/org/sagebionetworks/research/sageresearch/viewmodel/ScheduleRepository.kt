package org.sagebionetworks.research.sageresearch.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import org.joda.time.DateTime
import org.joda.time.Days
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider
import org.sagebionetworks.bridge.rest.model.Message
import org.sagebionetworks.bridge.rest.model.ScheduledActivity

import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4
import org.sagebionetworks.research.domain.result.interfaces.TaskResult
import org.sagebionetworks.research.sageresearch.dao.room.EntityTypeConverters
import org.sagebionetworks.research.sageresearch.dao.room.ResearchDatabase
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao
import org.sagebionetworks.research.sageresearch.extensions.isUnrecoverableError
import org.sagebionetworks.research.sageresearch.util.SingletonWithParam
import org.slf4j.LoggerFactory

import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action1
import rx.schedulers.Schedulers
import rx.subscriptions.CompositeSubscription
import java.util.UUID

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
open class ScheduleRepository(context: Context) {
    private val logger = LoggerFactory.getLogger(ScheduleRepository::class.java)

    /**
     * Creates a singleton that takes Context as a parameter to access
     * Use ScheduleRepository.getInstance(context)
     * TODO: mdephillips 9/2/18 use dagger?
     */
    companion object : SingletonWithParam<ScheduleRepository, Context>({
        ScheduleRepository(it.applicationContext)
    })

    private val entityConverter = EntityTypeConverters()

    private val scheduleDao: ScheduledActivityEntityDao =
            ResearchDatabase.getInstance(context).scheduleDao()

    /**
     * @property isSyncing true if the study's schedules are currently being fetched from bridge,
     *                     false if no syncing is currently occurring.
     */
    private var isSyncing = AtomicBoolean(false)
    /**
     * @property isSynced true if the study's schedules have been successfully fetched from bridge,
     *                    false if it still need done.
     */
    private var isSynced = AtomicBoolean(false)

    /**
     * @property scheduleRepoSubscriptions holds onto RxJava subscriptions if cleanup is ever required
     */
    private val scheduleRepoSubscriptions = CompositeSubscription()

    /**
     * Used to store helpful data about the state of the ScheduleRepository
     */
    private val prefs: SharedPreferences =
            context.getSharedPreferences("ScheduleRepository", MODE_PRIVATE)

    private val lastQueryDateKey = "lastQueryEndDate"
    /**
     * Save the local date time of the end date of the last request to bridge. Thread safe.
     */
    @VisibleForTesting
    protected open var lastQueryEndDate: DateTime?
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
    protected val scheduleTaskRunUuidMap = HashMap<UUID, String>()

    @VisibleForTesting
    protected open fun studyStartDate(): DateTime? {
        return BridgeDataProvider.getInstance().participantCreatedOn
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
    open val syncStartDate: DateTime? get() {
        return lastQueryEndDate?.withTimeAtStartOfDay() ?: run {
            studyStartDate()?.withTimeAtStartOfDay()
        }
    }

    /**
     * @property syncEndDate the end date time for the request to bridge to sync schedules
     */
    open val syncEndDate: DateTime get() {
        return now().plusDays(cachedDaysAhead)
    }

    /**
     * Makes sure the schedules we have cached are synced with the bridge server.
     * This function only operates if we are not currently syncing or have successfully synced
     */
    fun syncSchedules() {
        if (isSynced.get() || isSyncing.get()) {
            return // we are already retrieving the study's schedules
        }

        isSyncing.set(true)
        scheduleRepoSubscriptions.clear()

        val startDate = syncStartDate ?: return  // return if we aren't signed in yet
        val endDate = syncEndDate
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(startDate, endDate, maxRequestDays)

        // Use these atomic variables to track of the state of the requests
        val requestHasFailed = AtomicBoolean(false)
        val requestCounter = AtomicInteger(requestMap.keys.size)

        // Run all the requests, and join the results for the user
        for (start in requestMap.keys) {
            requestMap[start]?.let { end: DateTime ->
                scheduleRepoSubscriptions.add(BridgeDataProvider.getInstance().getActivities(start, end)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .subscribe({ activityListV4 ->
                            // If another request previously failed we won't trigger any success callbacks
                            if (!requestHasFailed.get()) {
                                requestCounter.set(requestCounter.get() - 1)
                                if (requestCounter.get() <= 0) {
                                    lastQueryEndDate = endDate
                                    isSynced.set(true)
                                    isSyncing.set(false)
                                }
                            }
                            // No matter if other requests failed, still cache a successful request
                            cacheSchedules(activityListV4)
                        }, { throwable ->
                            // Consider the sync a failure if one or more requests fail
                            if (!requestHasFailed.get()) {
                                isSynced.set(false)
                                isSyncing.set(false)
                                requestHasFailed.set(true)
                            }
                        }))

            }
        }
    }

    /**
     * When a schedule fails to update to Bridge for whatever reason, it will be marked in the db as so.
     * This function queries the db for those schedules and re-attempts to update them on Bridge.
     */
    fun syncFailedSchedules() {
        scheduleRepoSubscriptions.add(Observable.just(scheduleDao)
                .subscribeOn(Schedulers.io())
                .subscribe({ dao ->
                    val schedules = dao.activitiesThatNeedSyncedToBridge()
                    schedules.forEach {
                        updateScheduleToBridge(it)
                    }
                }, {
                    logger.warn(it.localizedMessage)
                }))
    }

    /**
     * Creates and associates the taskRunUuid and the schedule guid so that it can accessed later when complete
     * @param schedule of the task
     * @return the new associated uuid with this schedule
     */
    fun createScheduleTaskRunUuid(schedule: ScheduledActivityEntity): UUID {
        val uuid = UUID.randomUUID()
        scheduleTaskRunUuidMap[uuid] = schedule.guid
        return uuid
    }

    /**
     * @param schedule that we are going to update on bridge
     *                 schedule can be null if the user does a task without internet
     * @param taskResult the result of the user doing the schedule task
     */
    fun updateSchedule(taskResult: TaskResult) {
        // If we previously registered a guid with a taskRunUuid, we should be able to find the schedule in the
        // the database at this point based on the schedule guid
        findSchedule(taskResult.taskUUID, Action1 { schedule ->
            // TODO: mdephillips 9/14/2018 in past apps, here we would set client data from TaskResult
            // TODO: mdephillips 9/14/2018 instead use TaskResult to generate study report for schedule
            schedule.startedOn = taskResult.startTime
            schedule.finishedOn = taskResult.endTime
            cacheSchedule(schedule)
            updateScheduleToBridge(schedule)
        }, Action1 {
            // TODO: mdephillips 9/14/2018 message the user there will be no history?
            logger.warn(it.localizedMessage)
        })
    }

    /**
     * Private function should only be accessed through this class as a re-usable way to update
     * a schedule on bridge.
     */
    private fun updateScheduleToBridge(schedule: ScheduledActivityEntity) {
        val bridgeSchedule = schedule.clientWritableCopy()
        updateActivityOnBridge(bridgeSchedule, Action1 {
            schedule.needsSyncedToBridge = false
            cacheSchedule(schedule)
        }, Action1 {
            scheduleUpdateFailed(schedule, it)
        })
    }

    /**
     * Encapsulate db call functionality into it own function for providing custom mock behavior in tests,
     * and also to remove redundant threading code throughout the class.
     */
    @VisibleForTesting
    open fun findSchedule(
            taskRunUuid: UUID,
            onNext: Action1<ScheduledActivityEntity>,
            onError: Action1<Throwable>) {

        // Null schedule usually means that the developer forgot to call createScheduleTaskRunUuid before launching
        // the task associated with the schedule.
        // Or the user is offline and did a task without having synced the study's schedules.
        // Which, that is okay, the s3 upload will still take place, but we won't be able to update the schedule on bridge.
        // Side effects for this will be, no study reports or finishedOn status for the task will show in the UI.
        // TODO: mdephillips 9/14/2018 message the user there will be no history?
        val guid = scheduleTaskRunUuidMap[taskRunUuid] ?: run {
            onError.call(Throwable("No schedule guid found for taskRunUuid $taskRunUuid, " +
                    "are you sure you function createScheduleTaskRunUuid() before running the task?"))
            return
        }

        scheduleRepoSubscriptions.add(Observable.just(scheduleDao)
                .subscribeOn(Schedulers.io())
                .subscribe({ dao ->
                    dao.activity(guid).firstOrNull()?.let {
                        scheduleOnMain(it, onNext, onError)
                    } ?: run {
                        val error = Throwable("No schedule found in DB with guid $guid")
                        scheduleOnMain(error, onError)
                    }
                }, {
                    scheduleOnMain(it, onError)
                }))
    }

    /**
     * Helper function to schedule calls on the main thread
     */
    @VisibleForTesting
    protected open fun scheduleOnMain(
            schedule: ScheduledActivityEntity,
            onNext: Action1<ScheduledActivityEntity>,
            onError: Action1<Throwable>) {

        scheduleRepoSubscriptions.add(Observable.just(schedule)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onNext.call(it)
                }, {
                    onError.call(it)
                }))
    }

    /**
     * Helper function to schedule calls on the main thread
     */
    @VisibleForTesting
    protected open fun scheduleOnMain(
            throwable: Throwable,
            onError: Action1<Throwable>) {

        scheduleRepoSubscriptions.add(Observable.just(throwable)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onError.call(it)
                }, {
                    onError.call(it)
                }))
    }

    /**
     * @param schedule that failed to update on bridge.
     * @param throwable the reason the schedule failed to update on bridge.
     */
    private fun scheduleUpdateFailed(schedule: ScheduledActivityEntity, throwable: Throwable?) {
        throwable?.let {
            // There are some responses from the server that mean this call
            // will never succeed, like a 400 client data too large error.
            // If so, do not add the activity again to update.
            schedule.needsSyncedToBridge = !it.isUnrecoverableError()
        }
        ?: run {
            schedule.needsSyncedToBridge = true
        }

        cacheSchedule(schedule)
    }

    /**
     * Encapsulate network call functionality into it own function for providing custom mock behavior in tests,
     * and also to remove redundant threading code throughout the class.
     */
    @VisibleForTesting
    protected open fun updateActivityOnBridge(
            bridgeSchedule: ScheduledActivity,
            onNext: Action1<Message>,
            onError: Action1<Throwable>) {

        scheduleRepoSubscriptions.add(BridgeDataProvider.getInstance().updateActivity(bridgeSchedule)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io()).subscribe({ message ->
                    onNext.call(message)
                }, { throwable ->
                    onError.call(throwable)
                }))
    }

    /**
     * @param schedule to cache in the db, simply a helper function to convert to list
     */
    private fun cacheSchedule(schedule: ScheduledActivityEntity) {
        cacheSchedules(listOf(schedule))
    }

    /**
     * @param activityListV4 to convert to the entity format and cache
     */
    private fun cacheSchedules(activityListV4: ScheduledActivityListV4) {
        entityConverter.fromScheduledActivityListV4(activityListV4)?.let { list ->
            if (!reconcileWithDictionary()) {
                cacheSchedules(list)
            }
        }
    }

    /**
     * Encapsulate the db write operation in its own function for providing custom mock behavior in tests,
     * and also to remove redundant threading code throughout the class.
     */
    @VisibleForTesting
    protected open fun cacheSchedules(schedules: List<ScheduledActivityEntity>) {
        scheduleRepoSubscriptions.add(Observable.just(scheduleDao)
                .subscribeOn(Schedulers.io())
                .subscribe({ dao ->
                    dao.upsert(schedules)
                }, {
                    logger.warn(it.localizedMessage)
                }))
    }

    // TODO: mdephillips 9/2/18 correctly reconcile the schedules instead of doing an upsert
    fun reconcileWithDictionary(): Boolean {
        return false
        // For all but the client-writable fields, the server value is completely canonical.
        // For the client-writable fields, the client value is canonical unless it is nil.
        // @see ScheduleActivityEntity.clientWritableCopy()
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