/*
 * BSD 3-Clause License
 *
 * Copyright 2019  Sage Bionetworks. All rights reserved.
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

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.threeten.bp.LocalDate

@Dao
interface HistoryItemEntityDao {

    @Query("SELECT * FROM historyitementity ORDER BY dateBucket DESC, dateTime ASC")
    fun historyItems() : DataSource.Factory<Int, HistoryItemEntity>

    /**
     * Insert or update the given history item.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(historyItemEntity: HistoryItemEntity)

    /**
     * Deletes all rows in the table.  To be called on sign out or a cache clear.
     */
    @Query(RoomSql.HISTORY_DELETE)
    fun clear()

    /**
     * Delete all rows with the given type and dateBucket
     */
    @Query("DELETE FROM historyitementity where type = :type AND dateBucket = :dateBucket")
    fun delete(type: String, dateBucket: LocalDate)

    /**
     * As a single transaction, delete all rows with the given type and dateBucket,
     * then update with passed list of history items.
     */
    @Transaction
    fun deleteAndUpdate(type: String, dateBucket: LocalDate, items: List<HistoryItemEntity>) {
        delete(type, dateBucket)
        for (item in items) {
            upsert(item)
        }

    }

    /**
     * As a single transaction insert/update list of history items.
     */
    @Transaction
    fun update(items: List<HistoryItemEntity>) {
        for (item in items) {
            upsert(item)
        }

    }

}