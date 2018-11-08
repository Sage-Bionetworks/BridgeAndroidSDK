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

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate

/**
 * The ReportEntity contains a piece of data that was reported by the user at a specified date
 * It can be used to record any info about a task or survey that can be shown in the app later on
 */
@Entity
data class ReportEntity(
    /**
     * @property primaryKey
     */
    @PrimaryKey(autoGenerate = true) var primaryKey: Int = 0,  // 0 signals to room to auto-generate the id
    /**
     * @property identifier the report identifier
     */
    @ColumnInfo(index = true)
    var identifier: String? = null,
    /**
     * @property data open ended formatted data contained within the report
     */
    @SerializedName("data")
    var data: ClientData? = null,
    /**
     * @property dateTime of when the report was completed when ReportCategory is singleton or timestamp
     */
    @SerializedName("dateTime")
    @ColumnInfo(index = true)
    var dateTime: Instant? = null,
    /**
     * @property localDate of when the report was completed when ReportCategory is groupByDay
     */
    @SerializedName("localDate")
    @ColumnInfo(index = true)
    var localDate: LocalDate? = null,
    /**
     * @property needsSyncedToBridge is false if the state of this report is synced to bridge,
     *                               is true if the report was updated when the user was offline
     *                               reports should be queried on this field and updated on bridge when possible
     */
    @ColumnInfo(index = true)
    var needsSyncedToBridge: Boolean? = null)
