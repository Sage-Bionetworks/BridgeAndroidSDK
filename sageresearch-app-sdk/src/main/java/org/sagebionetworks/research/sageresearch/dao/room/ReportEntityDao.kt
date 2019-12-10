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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.annotation.VisibleForTesting
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate

@Dao
interface ReportEntityDao {

    /**
     * This may take a long time and use a lot of memory if the table is large, call with caution
     * @return all the reports in the table
     */
    @VisibleForTesting
    @Query(RoomSql.REPORT_QUERY_ALL)
    fun all(): List<ReportEntity>

    /**
     * @param reportEntityList to insert into the database
     * @return the auto-generated id of the ReportEntity that was added
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(reportEntityList: List<ReportEntity>): List<Long>

    /**
     * Use this query when you want reports that can have any Instant for their associated date
     * @param reportIdentifier reports returned will all have this report identifier
     * @param start of the time window to query for reports
     * @param end of the time window to query for reports
     * @return the reports with reportIdentifier and between start and end
     */
    @Query(RoomSql.SELECT_REPORTS_BETWEEN_DATE_TIME_WITH_IDENTIFIER)
    fun reports(reportIdentifier: String, start: Instant, end: Instant): LiveData<List<ReportEntity>>

    /**
     * Use this query when you want a reproducible start end search that is grouped by days
     * @param reportIdentifier reports returned will all have this report identifier
     * @param start of the time window to query for reports
     * @param end of the time window to query for reports
     * @return the reports with reportIdentifier and between start and end
     */
    @Query(RoomSql.SELECT_REPORTS_BETWEEN_LOCAL_DATE_WITH_IDENTIFIER)
    fun reports(reportIdentifier: String, start: LocalDate, end: LocalDate): LiveData<List<ReportEntity>>

    @Query(RoomSql.SELECT_REPORTS_BETWEEN_LOCAL_DATE_WITH_IDENTIFIER_REMOVE)
    fun reportsTODOREMOVE(reportIdentifier: String, start: LocalDate, end: LocalDate): LiveData<List<ReportEntity>>

    /**
     * Get all reports for a reportIdentifier based on the dateTime field
     * @param reportIdentifier reports returned will all have this report identifier
     * @return all reports with reportIdentifier or none if there aren't any saved yet
     */
    @Query(RoomSql.SELECT_ALL_REPORTS_WITH_DATE_IDENTIFIER)
    fun allReports(reportIdentifier: String): LiveData<List<ReportEntity>>

    /**
     * Get the most recent report for a reportIdentifier based on the dateTime field
     * @param reportIdentifier reports returned will all have this report identifier
     * @return the most recent report report with reportIdentifier or none if there aren't any saved yet
     */
    @Query(RoomSql.SELECT_MOST_RECENT_REPORT_WITH_DATE_IDENTIFIER)
    fun mostRecentReport(reportIdentifier: String): LiveData<List<ReportEntity>>

    /**
     * Get the most recent report for a reportIdentifier based on the dateTime field
     * @param reportIdentifier reports returned will all have this report identifier
     * @return the most recent report report with reportIdentifier or none if there aren't any saved yet
     */
    @Query(RoomSql.SELECT_MOST_RECENT_REPORT_WITH_DATE_IDENTIFIER)
    fun mostRecentReportInternal(reportIdentifier: String): List<ReportEntity>

    /**
     * Deletes all rows that match the query
     * @param reportIdentifier reports deleted will all have this report identifier
     * @param start of the time window to query for reports
     * @param end of the time window to query for reports
     */
    @Query(RoomSql.DELETE_REPORTS_BETWEEN_LOCAL_DATE_WITH_IDENTIFIER)
    fun delete(reportIdentifier: String, start: LocalDate, end: LocalDate)

    /**
     * Deletes all rows that match the query
     * @param reportIdentifier reports deleted will all have this report identifier
     * @param start of the time window to query for reports
     * @param end of the time window to query for reports
     */
    @Query(RoomSql.DELETE_REPORTS_BETWEEN_DATE_TIME_WITH_IDENTIFIER)
    fun delete(reportIdentifier: String, start: Instant, end: Instant)

    /**
     * @return the reports that have no been successfully synced to bridge yet
     */
    @Query(RoomSql.REPORTS_THAT_NEED_SYNCED)
    fun reportsThatNeedSyncedToBridge(): List<ReportEntity>

    /**
     * Deletes all rows in the table.  To be called on sign out or a cache clear.
     */
    @Query(RoomSql.REPORT_DELETE)
    fun clear()
}