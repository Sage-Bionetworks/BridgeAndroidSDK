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

import android.app.Application
import android.support.test.InstrumentationRegistry
import com.jakewharton.threetenabp.AndroidThreeTen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import org.sagebionetworks.research.sageresearch.dao.room.ClientData
import org.sagebionetworks.research.sageresearch.dao.room.RoomActivity
import org.sagebionetworks.research.sageresearch.dao.room.RoomTaskReference
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntity
import org.sagebionetworks.research.sageresearch.dao.room.bridgeMetadataCopy
import org.sagebionetworks.research.sageresearch.dao.room.clientWritableCopy
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime

class EntityTypeConverterTests {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupTests() {
            AndroidThreeTen.init(InstrumentationRegistry.getTargetContext())
        }
    }

    @Test
    fun test_clientWritableCopy() {
        val startOn = Instant.now()
        val finishedOn = Instant.now()
        val clientData = "{\"clientData\":true}"
        val guid = "guid"

        val scheduleEntity = ScheduledActivityEntity(guid)
        scheduleEntity.startedOn = startOn
        scheduleEntity.finishedOn = finishedOn
        scheduleEntity.clientData = ClientData(clientData)

        val schedule = scheduleEntity.clientWritableCopy()
        assertEquals(guid, schedule.guid)
        assertEquals(startOn.toEpochMilli(), schedule.startedOn.millis)
        assertEquals(finishedOn.toEpochMilli(), schedule.finishedOn.millis)
        assertEquals(clientData, schedule.clientData)
    }

    @Test
    fun test_bridgeMetadataCopy() {
        val startOn = Instant.now()
        val finishedOn = Instant.now()
        val guid = "guid"
        val schedulePlanGuid = "schedulePlanGuid"
        val taskId = "taskId"

        val scheduleEntity = ScheduledActivityEntity(guid)
        scheduleEntity.schedulePlanGuid = schedulePlanGuid
        scheduleEntity.startedOn = startOn
        scheduleEntity.finishedOn = finishedOn
        val scheduleOn = LocalDateTime.now()
        scheduleEntity.scheduledOn = scheduleOn
        scheduleEntity.activity = RoomActivity(guid)
        scheduleEntity.activity?.task = RoomTaskReference(taskId)

        val schedule = scheduleEntity.bridgeMetadataCopy()
        assertEquals(guid, schedule.guid)
        assertEquals(schedulePlanGuid, schedule.schedulePlanGuid)
        assertEquals(startOn.toEpochMilli(), schedule.startedOn.millis)
        assertEquals(finishedOn.toEpochMilli(), schedule.finishedOn.millis)

        assertEquals(scheduleOn.year, schedule.scheduledOn.year)
        assertEquals(scheduleOn.dayOfYear, schedule.scheduledOn.dayOfYear)
        assertEquals(scheduleOn.hour, schedule.scheduledOn.hourOfDay)
        assertEquals(scheduleOn.minute, schedule.scheduledOn.minuteOfHour)
        assertEquals(scheduleOn.second, schedule.scheduledOn.secondOfMinute)

        assertNotNull(scheduleEntity.activity)
        assertNotNull(scheduleEntity.activity?.task)
        assertNotNull(scheduleEntity.activity?.task?.identifier)
        assertEquals(taskId, scheduleEntity.activity?.task?.identifier)
    }
}