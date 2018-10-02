package org.sagebionetworks.research.sageresearch.dao.room

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverters
import android.content.Context
import org.sagebionetworks.research.sageresearch.util.SingletonWithParam

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

@Database(entities = arrayOf(ScheduledActivityEntity::class),
        version = 1)
@TypeConverters(EntityTypeConverters::class)
abstract class ResearchDatabase : RoomDatabase() {

    /**
     * Creates a singleton that takes Context as a parameter to access
     * Use ResearchDatabase.getInstance(context)
     * TODO: mdephillips 9/2/18 use dagger?
     */
    companion object : SingletonWithParam<ResearchDatabase, Context>({
        Room.databaseBuilder(it.applicationContext,
                ResearchDatabase::class.java, "ResearchDatabase.db")
                .build()
    })

    abstract fun scheduleDao(): ScheduledActivityEntityDao
}

internal class RoomSql {
    /**
     * Because all Room queries need to be verified at compile time, we cannot build dynamic queries based on state.
     * This is where these constant string values come into play, as building blocks to form reliable Room queries.
     * These originally came about to model re-usable query components like iOS' NSPredicates and CoreData.
     */
    companion object RoomSqlConstants {

        /**
         * OP constants combing CONDITION constants
         */
        private const val OP_AND = " AND "
        private const val OP_OR = " OR "

        /**
         * DELETE constants delete tables
         */
        const val SCHEDULE_DELETE = "DELETE FROM scheduledactivityentity"

        /**
         * SELECT constants start off queries
         */
        private const val SCHEDULE_SELECT = "SELECT * FROM scheduledactivityentity WHERE "

        /**
         * ORDER BY constants do sorting on queries
         */

        private const val ORDER_BY_FINISHED = " ORDER BY finishedOn DESC"

        /**
         * LIMIT constants restrict the number of db rows
         */
        private const val LIMIT_1 = " LIMIT 1"

        /**
         * CONDITION constants need to be joined by AND or OR in the select statement
         */

        private const val SCHEDULE_CONDITION_GUID = "guid = :guid"

        private const val SCHEDULE_CONDITION_ACTIVITY_GROUP_ID =
                "(activity_task_identifier IN (:activityGroup) OR " +
                "activity_survey_identifier IN (:activityGroup) OR " +
                "activity_compound_taskIdentifier IN (:activityGroup))"

        private const val SCHEDULE_CONDITION_EXCLUDE_ACTIVITY_GROUP_ID =
                "((activity_task_identifier IS NULL OR activity_task_identifier NOT IN (:activityGroup)) AND " +
                "(activity_survey_identifier IS NULL OR activity_survey_identifier NOT IN (:activityGroup)) AND " +
                "(activity_compound_taskIdentifier IS NULL OR activity_compound_taskIdentifier NOT IN (:activityGroup)))"

        private const val SCHEDULE_CONDITION_EXCLUDE_SURVEY_GROUP_ID =
                "(activity_survey_identifier IS NOT NULL AND activity_survey_identifier NOT IN (:surveyGroup))"

        private const val SCHEDULE_CONDITION_NOT_FINISHED = "(finishedOn IS NULL)"
        private const val SCHEDULE_CONDITION_FINISHED = "(finishedOn IS NOT NULL)"
        private const val SCHEDULE_CONDITION_FINISHED_BETWEEN = "(finishedOn BETWEEN :finishedStart AND :finishedEnd)"
        private const val SCHEDULE_CONDITION_FINISHED_BETWEEN_OR_NULL =
                "($SCHEDULE_CONDITION_NOT_FINISHED$OP_OR$SCHEDULE_CONDITION_FINISHED_BETWEEN)"

        // Room doesn't have boolean type and maps true = 1 and false = 0
        private const val SCHEDULE_CONDITION_NEEDS_SYNCED_TO_BRIDGE =
                "(needsSyncedToBridge IS NOT NULL AND needsSyncedToBridge = 1)"

        private const val SCHEDULE_CONDITION_NO_EXPIRES_DATE = "(expiresOn IS NULL)"
        private const val SCHEDULE_CONDITION_HAS_EXPIRES_DATE = "(expiresOn IS NOT NULL)"
        private const val SCHEDULE_CONDITION_EXPIRES_BETWEEN = "(expiresOn BETWEEN :start AND :end)"
        private const val SCHEDULE_CONDITION_EXPIRES_BETWEEN_OR_NULL =
                "($SCHEDULE_CONDITION_NO_EXPIRES_DATE$OP_OR$SCHEDULE_CONDITION_EXPIRES_BETWEEN)"

        private const val SCHEDULE_CONDITION_AVAILABLE_DATE =
                "((:date BETWEEN scheduledOn AND expiresOn) OR " +
                        "(expiresOn IS NULL AND :date >= scheduledOn))"

        private const val SCHEDULE_CONDITION_BETWEEN_DATES =
                "(" + SCHEDULE_CONDITION_FINISHED_BETWEEN_OR_NULL + OP_AND +
                        SCHEDULE_CONDITION_EXPIRES_BETWEEN_OR_NULL + OP_AND +
                        "(scheduledOn <= :end)" + ")"

        /**
         * QUERY constants are full Room queries
         */
        const val SCHEDULE_QUERY_ALL = "SELECT * FROM scheduledactivityentity"

        const val SCHEDULE_QUERY_SELECT_GUID =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_GUID

        const val SCHEDULE_QUERY_SELECT_ACTIVITY_GROUP =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_ACTIVITY_GROUP_ID

        const val SCHEDULE_QUERY_SELECT_AVAILABLE_DATE =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_AVAILABLE_DATE

        const val SCHEDULE_QUERY_SELECT_NOT_FINISHED_AVAILABLE_DATE =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_AVAILABLE_DATE + OP_AND + SCHEDULE_CONDITION_NOT_FINISHED

        const val SCHEDULE_QUERY_SELECT_ACTIVITY_GROUP_AVAILABLE_DATE =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_AVAILABLE_DATE + OP_AND + SCHEDULE_CONDITION_ACTIVITY_GROUP_ID

        const val SCHEDULE_QUERY_SELECT_ACTIVITY_GROUP_BETWEEN_DATE_UNFINISHED_OR_FINISHED_BETWEEN =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_ACTIVITY_GROUP_ID + OP_AND + SCHEDULE_CONDITION_BETWEEN_DATES

        const val SCHEDULE_QUERY_ACTIVITY_GROUP_FINISHED_BETWEEN =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_ACTIVITY_GROUP_ID +
                OP_AND + SCHEDULE_CONDITION_FINISHED + OP_AND + SCHEDULE_CONDITION_FINISHED_BETWEEN

        const val SCHEDULE_QUERY_EXCLUDE_ACTIVITY_GROUP_FINISHED_BETWEEN =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_EXCLUDE_ACTIVITY_GROUP_ID +
                OP_AND + SCHEDULE_CONDITION_FINISHED + OP_AND + SCHEDULE_CONDITION_FINISHED_BETWEEN

        const val SCHEDULE_QUERY_EXCLUDE_SURVEY_GROUP_UNFINISHED_AVAILABLE_DATE =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_EXCLUDE_SURVEY_GROUP_ID +
                        OP_AND + SCHEDULE_CONDITION_NOT_FINISHED + OP_AND + SCHEDULE_CONDITION_AVAILABLE_DATE

        const val SCHEDULE_MOST_RECENT_FINISHED_ACTIVITY =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_ACTIVITY_GROUP_ID + OP_AND +
                        SCHEDULE_CONDITION_FINISHED + ORDER_BY_FINISHED + LIMIT_1

        const val SCHEDULE_ACTIVITIES_THAT_NEED_SYNCED =
                SCHEDULE_SELECT + SCHEDULE_CONDITION_NEEDS_SYNCED_TO_BRIDGE
    }
}

