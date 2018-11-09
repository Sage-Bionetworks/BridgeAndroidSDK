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

import android.support.test.filters.MediumTest
import android.support.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.mapValue
import org.threeten.bp.LocalDate
import org.threeten.bp.ZonedDateTime

//
//  Copyright © 2016-2018 Sage Bionetworks. All rights reserved.
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

@RunWith(AndroidJUnit4::class)
// ran into multi-dex issues moving this to a library project, leaving it here for now
@MediumTest
class RoomReportTests: RoomTestHelper() {

    companion object {
        val reportIdentifierV3 = "reportV3"
        val reportIdentifierV4 = "reportV4"
        val reportEntityListAll = "test_reports_all.json"
        val reportEntityList = TestResourceHelper.testResourceReportEntityList(reportEntityListAll)
    }

    @Before
    fun setupForEachTestWithEmptyDatabase() {
        reportDao.clear()
    }

    @Test
    fun test_clear() {
        reportDao.upsert(reportEntityList)
        reportDao.clear()
        assertEquals(0, reportDao.all().size)
    }

    @Test
    fun test_insert() {
        reportDao.upsert(reportEntityList)
        assertReportsContain(listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), reportDao.all())
    }

    @Test
    fun query_testLocalDate() {
        reportDao.upsert(reportEntityList)
        val start = LocalDate.parse("2018-11-07")
        val end = LocalDate.parse("2018-11-09")
        val allReports = getValue(reportDao.reports(reportIdentifierV3, start, end))
        assertReportsContain(listOf("0", "1", "2", "3"), allReports)
    }

    @Test
    fun query_testDateTime() {
        reportDao.upsert(reportEntityList)
        val start = ZonedDateTime.parse("2018-11-07T00:00:00.000Z").toInstant()
        val end = ZonedDateTime.parse("2018-11-10T00:00:00.000Z").toInstant()
        val allReports = getValue(reportDao.reports(reportIdentifierV4, start, end))
        assertReportsContain(listOf("5", "6", "7", "8"), allReports)
    }

    @Test
    fun query_testNeedsSynced() {
        reportDao.upsert(reportEntityList)
        assertEquals(0, reportDao.reportsThatNeedSyncedToBridge().size)
        reportDao.upsert(reportDao.all().filter {
            it.data?.mapValue("guid")?.let { value ->
                return@filter listOf("0", "1", "2", "3", "4").contains(value)
            }
            return@filter false
        }.map {
            it.copy(needsSyncedToBridge = true)
        })
        val allReports = reportDao.reportsThatNeedSyncedToBridge()
        assertReportsContain(listOf("0", "1", "2", "3", "4"), allReports)
    }

    @Test
    fun query_testMostRecentLocalDate() {
        reportDao.upsert(reportEntityList)
        val mosRecent = getValue(reportDao.mostRecentReport(reportIdentifierV3))
        assertReportsContain(listOf("4"), mosRecent)
    }

    @Test
    fun query_testMostRecentDateTime() {
        reportDao.upsert(reportEntityList)
        val mosRecent = getValue(reportDao.mostRecentReport(reportIdentifierV4))
        assertReportsContain(listOf("9"), mosRecent)
    }

    @Test
    fun delete_testLocalDate() {
        reportDao.upsert(reportEntityList)
        val start = LocalDate.parse("2018-11-07")
        val end = LocalDate.parse("2018-11-09")
        reportDao.delete(reportIdentifierV3, start, end)
        val allReports = reportDao.all()
        assertReportsContain(listOf("4", "5", "6", "7", "8", "9"), allReports)
    }

    @Test
    fun delete_testDateTime() {
        reportDao.upsert(reportEntityList)
        val start = ZonedDateTime.parse("2018-11-07T00:00:00.000Z").toInstant()
        val end = ZonedDateTime.parse("2018-11-10T00:00:00.000Z").toInstant()
        reportDao.delete(reportIdentifierV4, start, end)
        val allReports = reportDao.all()
        assertReportsContain(listOf("0", "1", "2", "3", "4", "9"), allReports)
    }

    fun assertReportsContain(clientDataGuid: List<String>, reportList: List<ReportEntity>) {
        assertEquals(clientDataGuid.size, reportList.size)
        assertEquals(0, reportList.filter { report ->
            report.data?.mapValue("guid")?.let {
                clientDataGuid.contains(it)
            }
            false
        }.size)
    }
}