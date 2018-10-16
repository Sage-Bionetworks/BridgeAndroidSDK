package org.sagebionetworks.research.sageresearch.dao.room

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime

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
 * All interactions with the ScheduledActivityEntity table will be done through this interface.
 * Room auto-generates the implementations of these methods as well as checks the syntax of the SQL statements
 * This class is designed to work @see ScheduleViewModel using LiveData DB returns
 * TODO: mdephillips 8/20/18 create the rest of the queries in iOS code repo file 'SBBScheduledActivity+Filters'
 * TODO: @liujoshua 2018/10/05 for queries that should return zero or one values, instead of a collection,
 *      return a @Nullable ScheduleActivityEntity, Optional<ScheduleActivityEntity>, Maybe<ScheduleActivityEntity>
 */
@Dao
interface ScheduledActivityEntityDao {

    /**
     * Get all the scheduled activities from the table
     * This may take a long time and use a lot of memory if the table is large, call with caution
     * @return all the scheduled activities in the table
     */
    @Query(RoomSql.SCHEDULE_QUERY_ALL)
    fun all(): LiveData<List<ScheduledActivityEntity>>

    /**
     * @param guid to query on
     * @return the scheduled activity from the table with the specified guid
     */
    @Query(RoomSql.SCHEDULE_QUERY_SELECT_GUID)
    fun activity(guid: String): List<ScheduledActivityEntity>

    /**
     * Get all the scheduled activities that have one of the identifiers specified in the task group
     * @param activityGroup a set of identifiers to use as a filter for scheduled items
     * @return the list of scheduled activities
     */
    @Query(RoomSql.SCHEDULE_QUERY_SELECT_ACTIVITY_GROUP)
    fun activityGroup(activityGroup: Set<String>): LiveData<List<ScheduledActivityEntity>>

    /**
     * Get all the scheduled activities that are scheduled during and not expired yet during this date
     * @param date to filter the scheduled activities
     * @return the list of scheduled activities
     */
    @Query(RoomSql.SCHEDULE_QUERY_SELECT_AVAILABLE_DATE)
    fun availableOn(date: LocalDateTime): LiveData<List<ScheduledActivityEntity>>

    /**
     * Get all the scheduled activities that are available (not finished yet) and available schedule-wise at this date
     * @param date to filter the scheduled activities
     * @return the list of scheduled activities
     */
    @Query(RoomSql.SCHEDULE_QUERY_SELECT_NOT_FINISHED_AVAILABLE_DATE)
    fun unfinishedAvailableOn(date: LocalDateTime): LiveData<List<ScheduledActivityEntity>>

    /**
     * Get all the scheduled activities that are available schedule-wise at this date and are in this task group
     * @param activityGroup to filter the scheduled activities
     * @param date to filter the scheduled activities
     * @return the list of scheduled activities
     */
    @Query(RoomSql.SCHEDULE_QUERY_SELECT_ACTIVITY_GROUP_AVAILABLE_DATE)
    fun activityGroupAvailableOn(activityGroup: Set<String>, date: LocalDateTime): LiveData<List<ScheduledActivityEntity>>

    /**
     * Get all the scheduled activities that are available schedule-wise between these dates and are in this group
     * @param activityGroup to filter the scheduled activities
     * @param start to filter the scheduled activities
     * @param end to filter the scheduled activities
     * @param finishedStart start of the finished on bounds (usually the same as start but an Instant)
     * @param finishedEnd end of the finished on bounds (usually the same as end but an Instant)
     * @return the list of scheduled activities
     */
    @Query(RoomSql.SCHEDULE_QUERY_SELECT_ACTIVITY_GROUP_BETWEEN_DATE_UNFINISHED_OR_FINISHED_BETWEEN)
    fun activityGroupAvailableBetween(activityGroup: Set<String>,
            start: LocalDateTime, end: LocalDateTime,
            finishedStart: Instant, finishedEnd: Instant): LiveData<List<ScheduledActivityEntity>>

    /**
     * Get all the scheduled activities that were finished between the two dates
     * @param activityGroup to filter the scheduled activities
     * @param finishedStart of the bounds where activity was finished
     * @param finishedEnd of the bounds where activity was finished
     * @return the list of scheduled activities
     */
    @Query(RoomSql.SCHEDULE_QUERY_ACTIVITY_GROUP_FINISHED_BETWEEN)
    fun activityGroupFinishedBetween(activityGroup: Set<String>, finishedStart: Instant, finishedEnd: Instant): LiveData<List<ScheduledActivityEntity>>

    /**
     * Get all the scheduled activities that were finished between the two dates
     * @param activityGroup that will be excluded from results
     * @param finishedStart of the bounds where activity was finished
     * @param finishedEnd of the bounds where activity was finished
     * @return the list of scheduled activities
     */
    @Query(RoomSql.SCHEDULE_QUERY_EXCLUDE_ACTIVITY_GROUP_FINISHED_BETWEEN)
    fun excludeActivityGroupFinishedBetween(activityGroup: Set<String>, finishedStart: Instant, finishedEnd: Instant): LiveData<List<ScheduledActivityEntity>>

    /**
     * Get all the scheduled activities that are surveys that do not have identifiers in the survey group
     * @param surveyGroup that will be excluded from results, and results will only be surveys
     * @param date to filter the scheduled activities
     * @return the list of scheduled activities
     */
    @Query(RoomSql.SCHEDULE_QUERY_EXCLUDE_SURVEY_GROUP_UNFINISHED_AVAILABLE_DATE)
    fun excludeSurveyGroupUnfinishedAvailableOn(surveyGroup: Set<String>, date: LocalDateTime): LiveData<List<ScheduledActivityEntity>>

    /**
     * Get the most recently finished activity in the activity group
     * @param activityGroup that will be included in results
     * @return the most recently finished schedule that matches the query
     */
    @Query(RoomSql.SCHEDULE_MOST_RECENT_FINISHED_ACTIVITY)
    fun mostRecentFinishedActivity(activityGroup: Set<String>): LiveData<List<ScheduledActivityEntity>>

    /**
     * @return all the activities where activity.needsSyncedToBridge is true
     */
    @Query(RoomSql.SCHEDULE_ACTIVITIES_THAT_NEED_SYNCED)
    fun activitiesThatNeedSyncedToBridge(): List<ScheduledActivityEntity>

    /**
     * @param roomScheduledActivityList to insert into the database
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(roomScheduledActivityList: List<ScheduledActivityEntity>)

    /**
     * Deletes all rows in the table.  To be called on sign out or a cache clear.
     */
    @Query(RoomSql.SCHEDULE_DELETE)
    fun clear()
}