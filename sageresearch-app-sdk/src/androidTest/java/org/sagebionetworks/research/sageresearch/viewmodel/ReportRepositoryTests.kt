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

import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import org.joda.time.DateTimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.MockitoAnnotations
import org.sagebionetworks.bridge.android.BridgeConfig
import org.sagebionetworks.bridge.android.BridgeConfig.ReportCategory
import org.sagebionetworks.bridge.android.manager.ParticipantRecordManager
import org.sagebionetworks.research.sageresearch.dao.room.ClientData
import org.sagebionetworks.research.sageresearch.dao.room.EntityTypeConverters
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.dao.room.entityCopy
import org.sagebionetworks.research.sageresearch.dao.room.mapValue
import org.sagebionetworks.research.sageresearch.extensions.toJodaLocalDate
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZoneOffset
import rx.Single

class ReportRepositoryTests: RoomTestHelper() {

    companion object {
        val reportListV3 = "test_reports_v3.json"
        val reportListSingletonV3 = "test_reports_v3_singleton.json"
        val reportListV4Page1 = "test_reports_v4_1.json"
        val reportListV4Page2 = "test_reports_v4_2.json"
        val reportListV4Page3 = "test_reports_v4_3.json"

        val reportDataListV3 = TestResourceHelper.testResourceReportDataList(reportListV3)
        val reportEntityListV3 = reportDataListV3.items.map {
            it.entityCopy(RoomReportTests.reportIdentifierV3)
        }
        val reportDataListSingletonV3 = TestResourceHelper.testResourceReportDataList(reportListSingletonV3)

        val reportCursorV4Page1 = TestResourceHelper.testResourceForwardCursorReportDataList(reportListV4Page1)
        val reportCursorV4Page2 = TestResourceHelper.testResourceForwardCursorReportDataList(reportListV4Page2)
        val reportCursorV4Page3 = TestResourceHelper.testResourceForwardCursorReportDataList(reportListV4Page3)
        val reportEntityListV4 = listOf(reportCursorV4Page1, reportCursorV4Page1, reportCursorV4Page1)
                .flatMap {
                    it.items
                }.map {
                    it.entityCopy(RoomReportTests.reportIdentifierV4)
                }

        val bridgeGson = EntityTypeConverters().bridgeGson
    }

    lateinit var reportRepository: MockReportRepository
    @Mock
    lateinit var participantManager: ParticipantRecordManager
    @Mock
    lateinit var bridgeConfig: BridgeConfig

    @Before
    fun setupBeforeEachTest() {
        reportDao.clear()
        MockitoAnnotations.initMocks(this)
        reportRepository = MockReportRepository(participantManager, bridgeConfig)
    }

    @Test
    fun test_buildClientDataSurvey() {
        val clientData = reportRepository.buildClientData(TaskResultHelper.surveyTaskResult())
        assertNotNull(clientData)
        clientData?.let {
            assertMapEquals(TaskResultHelper.expectedSurveyClientDataMap, it)
        }
    }

    @Test
    fun test_buildClientDataResearchStackSurvey() {
        val clientData = reportRepository.buildClientData(TaskResultHelper.researchStackSurveyTaskResult())
        assertNotNull(clientData)
        clientData?.let {
            assertMapEquals(TaskResultHelper.expectedSurveyClientDataMap, it)
        }
    }

    @Test
    fun test_fetchReportsTimestamp() {
        val reportIdentifier = MockReportRepository.reportIdentifierV4
        val start = LocalDateTime.parse("2018-11-07T00:00:00")
        val startInstant = start.toInstant(ZoneOffset.UTC)
        val startJodaDateTime = org.joda.time.DateTime(startInstant.toEpochMilli(), DateTimeZone.UTC)
        val end = LocalDateTime.parse("2018-11-10T00:00:00")
        val endInstant = end.toInstant(ZoneOffset.UTC)
        val endJodaDateTime = org.joda.time.DateTime(endInstant.toEpochMilli(), DateTimeZone.UTC)
        assertEquals(0, getValue(reportDao.reports(reportIdentifier, startInstant, endInstant)).size)

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(reportCursorV4Page1))  // page 1 contains 2 reports
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, startJodaDateTime, endJodaDateTime, reportRepository.reportPageSizeV4, null)

        doReturn(Single.just(reportCursorV4Page2))  // page 2 contains 2 reports, offset key is "2", see "test_reports_v4_1.json"
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, startJodaDateTime, endJodaDateTime, reportRepository.reportPageSizeV4, "2")

        doReturn(Single.just(reportCursorV4Page3))  // page 3 contains 1 report, offset key is "3", see "test_reports_v4_2.json"
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, startJodaDateTime, endJodaDateTime, reportRepository.reportPageSizeV4, "3")

        // Fetch reports should trigger a timestamp style fetch to v4 that will request the doReturn pages above
        reportRepository.fetchReports(reportIdentifier, start, end)
        assertEquals(5, getValue(reportDao.reports(reportIdentifier, startInstant, endInstant)).size)
    }

    @Test
    fun test_fetchReportsTimestampIncompletePages() {
        val reportIdentifier = MockReportRepository.reportIdentifierV4
        val start = LocalDateTime.parse("2018-11-07T00:00:00")
        val startInstant = start.toInstant(ZoneOffset.UTC)
        val startJodaDateTime = org.joda.time.DateTime(startInstant.toEpochMilli(), DateTimeZone.UTC)
        val end = LocalDateTime.parse("2018-11-10T00:00:00")
        val endInstant = end.toInstant(ZoneOffset.UTC)
        val endJodaDateTime = org.joda.time.DateTime(endInstant.toEpochMilli(), DateTimeZone.UTC)
        assertEquals(0, getValue(reportDao.reports(reportIdentifier, startInstant, endInstant)).size)

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(reportCursorV4Page1))  // page 1 contains 2 reports, no next offset key
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, startJodaDateTime, endJodaDateTime, reportRepository.reportPageSizeV4, null)

        doReturn(Single.just(Throwable("Failed to get page 2")))
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, startJodaDateTime, endJodaDateTime, reportRepository.reportPageSizeV4, "2")

        // Fetch reports should trigger a timestamp style fetch to v4 that will error on second page
        reportRepository.fetchReports(reportIdentifier, start, end)
        // The first page should still save to the database correctly
        assertEquals(2, getValue(reportDao.reports(reportIdentifier, startInstant, endInstant)).size)
    }

    @Test
    fun test_fetchReportsTimestampFailedNoInternet() {
        val reportIdentifier = MockReportRepository.reportIdentifierV4
        val start = LocalDateTime.parse("2018-11-07T00:00:00")
        val startInstant = start.toInstant(ZoneOffset.UTC)
        val startJodaDateTime = org.joda.time.DateTime(startInstant.toEpochMilli(), DateTimeZone.UTC)
        val end = LocalDateTime.parse("2018-11-10T00:00:00")
        val endInstant = end.toInstant(ZoneOffset.UTC)
        val endJodaDateTime = org.joda.time.DateTime(endInstant.toEpochMilli(), DateTimeZone.UTC)
        assertEquals(0, getValue(reportDao.reports(reportIdentifier, startInstant, endInstant)).size)

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(Throwable("Failed to get page 1")))
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, startJodaDateTime, endJodaDateTime, reportRepository.reportPageSizeV4, null)

        // Fetch reports should trigger a timestamp style fetch to v4 that will error on first page
        reportRepository.fetchReports(reportIdentifier, start, end)
        // The first page should still save to the database correctly
        assertEquals(0, getValue(reportDao.reports(reportIdentifier, startInstant, endInstant)).size)
    }

    @Test
    fun test_fetchReportsGroupByDay() {
        // the MockReportRepository will map this to the group by day category
        val reportIdentifier = MockReportRepository.reportIdentifierV3
        val start = LocalDate.parse("2018-11-07")
        val end = LocalDate.parse("2018-11-10")
        assertEquals(0, getValue(reportDao.reports(reportIdentifier, start, end)).size)

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(reportDataListV3)) // contains 5 reports
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV3(reportIdentifier, start.toJodaLocalDate(), end.toJodaLocalDate())

        reportRepository.fetchReports(reportIdentifier, start.atTime(0, 0), end.atTime(0, 0))
        // The first page should still save to the database correctly
        assertEquals(5, getValue(reportDao.reports(reportIdentifier, start, end)).size)
    }

    @Test
    fun test_fetchReportsGroupByDayNoInternet() {
        val reportIdentifier = MockReportRepository.reportIdentifierV3
        val start = LocalDate.parse("2018-11-07")
        val end = LocalDate.parse("2018-11-10")
        assertEquals(0, getValue(reportDao.reports(reportIdentifier, start, end)).size)

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(Throwable("Failed to get record")))
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV3(reportIdentifier, start.toJodaLocalDate(), end.toJodaLocalDate())

        reportRepository.fetchReports(reportIdentifier, start.atTime(0, 0), end.atTime(0, 0))
        // The first page should still save to the database correctly
        assertEquals(0, getValue(reportDao.reports(reportIdentifier, start, end)).size)
    }

    @Test
    fun test_fetchReportsSingleton() {
        // the MockReportRepository will map this to the singleton category
        val reportIdentifier = MockReportRepository.reportIdentifierV3Singleton
        val start = LocalDate.parse("2018-11-07")  // this doesn't matter, singleton date will be used
        val startLocalDateTime = start.atTime(0, 0)
        val end = LocalDate.parse("2018-11-10")  // these doesn't matter, singleton date will be used
        val endLocalDateTime = end.atTime(0, 0)
        assertEquals(0, getValue(reportDao.reports(reportIdentifier,
                reportRepository.reportSingletonLocalDate, reportRepository.reportSingletonLocalDate)).size)

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(reportDataListSingletonV3)) // contains 5 reports
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV3(reportIdentifier,
                        reportRepository.reportSingletonJodaLocalDate,
                        reportRepository.reportSingletonJodaLocalDate)

        reportRepository.fetchReports(reportIdentifier, startLocalDateTime, endLocalDateTime)
        // The first page should still save to the database correctly
        assertEquals(5, getValue(reportDao.reports(reportIdentifier,
                reportRepository.reportSingletonLocalDate, reportRepository.reportSingletonLocalDate)).size)
    }

    @Test
    fun test_fetchReportsMostRecentV4() {
        // the MockReportRepository will map this to the timestamp category
        val reportIdentifier = MockReportRepository.reportIdentifierV4
        assertEquals(0, getValue(reportDao.mostRecentReport(reportIdentifier)).size)

        val end = reportRepository.nowVal
        val start = reportRepository.studyStartDate() ?: end

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(reportCursorV4Page1))  // page 1 contains 2 reports
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, start, end, reportRepository.reportPageSizeV4, null)

        doReturn(Single.just(reportCursorV4Page2))  // page 2 contains 2 reports, offset key is "2", see "test_reports_v4_1.json"
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, start, end, reportRepository.reportPageSizeV4, "2")

        doReturn(Single.just(reportCursorV4Page3))  // page 3 contains 1 report, offset key is "3", see "test_reports_v4_2.json"
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, start, end, reportRepository.reportPageSizeV4, "3")

        reportRepository.fetchMostRecentReportIfNotCached(reportIdentifier)
        // The first page should still save to the database correctly
        val mostRecent = getValue(reportDao.mostRecentReport(reportIdentifier))
        assertReportsContain(listOf("4"), mostRecent)
    }

    @Test
    fun test_fetchReportsNoFetchNeededV4() {
        // the MockReportRepository will map this to the groupByDay
        val reportIdentifier = MockReportRepository.reportIdentifierV3Singleton
        reportDao.upsert(listOf(ReportEntity(identifier = reportIdentifier,
                data = ClientData("5"),
                localDate = reportRepository.reportSingletonLocalDate)))
        assertReportsContain(listOf("5"), getValue(reportDao.mostRecentReport(reportIdentifier)))

        val end = reportRepository.nowVal
        val start = reportRepository.studyStartDate() ?: end

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(reportCursorV4Page1))  // page 1 contains 2 reports
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, start, end, reportRepository.reportPageSizeV4, null)

        doReturn(Single.just(reportCursorV4Page2))  // page 2 contains 2 reports, offset key is "2", see "test_reports_v4_1.json"
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, start, end, reportRepository.reportPageSizeV4, "2")

        doReturn(Single.just(reportCursorV4Page3))  // page 3 contains 1 report, offset key is "3", see "test_reports_v4_2.json"
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV4(reportIdentifier, start, end, reportRepository.reportPageSizeV4, "3")

        reportRepository.fetchMostRecentReportIfNotCached(reportIdentifier)
        // The fetch request should never happen, and therefore, the "4" guid should not be the newest
        // even though it would be the newest if the fetch went through
        val mostRecent = getValue(reportDao.mostRecentReport(reportIdentifier))
        assertReportsContain(listOf("5"), mostRecent)
    }

    @Test
    fun test_fetchReportsMostRecentV3() {
        // the MockReportRepository will map this to the groupByDay
        val reportIdentifier = MockReportRepository.reportIdentifierV3Singleton
        assertEquals(0, getValue(reportDao.mostRecentReport(reportIdentifier)).size)

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(reportDataListSingletonV3)) // contains 5 reports
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV3(reportIdentifier,
                        reportRepository.reportSingletonJodaLocalDate,
                        reportRepository.reportSingletonJodaLocalDate)

        reportRepository.fetchMostRecentReportIfNotCached(reportIdentifier)
        // The first page should still save to the database correctly
        val mostRecent = getValue(reportDao.mostRecentReport(reportIdentifier))
        assertReportsContain(listOf("4"), mostRecent)
    }

    @Test
    fun test_fetchReportsNoFetchNeededV3() {
        // the MockReportRepository will map this to the groupByDay
        val reportIdentifier = MockReportRepository.reportIdentifierV3Singleton
        reportDao.upsert(listOf(ReportEntity(identifier = reportIdentifier,
                data = ClientData("5"),
                localDate = reportRepository.reportSingletonLocalDate)))
        assertReportsContain(listOf("5"), getValue(reportDao.mostRecentReport(reportIdentifier)))

        // Set up the participantManager to return the correct report page sequence
        doReturn(Single.just(reportDataListSingletonV3)) // contains 5 reports
                .`when`<ParticipantRecordManager>(participantManager)
                .getReportsV3(reportIdentifier,
                        reportRepository.reportSingletonJodaLocalDate,
                        reportRepository.reportSingletonJodaLocalDate)

        reportRepository.fetchMostRecentReportIfNotCached(reportIdentifier)
        // The fetch request should never happen, and therefore, the "4" guid should not be the newest
        // even though it would be the newest if the fetch went through
        val mostRecent = getValue(reportDao.mostRecentReport(reportIdentifier))
        assertReportsContain(listOf("5"), mostRecent)
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

    fun assertMapEquals(expected: Map<String, Any>, actual: Map<String, Any>) {
        assertEquals(expected.size, actual.size)
        expected.forEach {
            assertTrue(actual.containsKey(it.key))
            assertEquals(expected[it.key], actual[it.key])
        }
    }

    /**
     * Mock to control page size, report category mapping, and threading
     */
    class MockReportRepository(
            participantManager: ParticipantRecordManager, bridgeConfig: BridgeConfig):
            ReportRepository(reportDao, participantManager, bridgeConfig) {

        companion object {
            val reportIdentifierV3 = "reportV3"
            val reportIdentifierV4 = "reportV4"
            val reportIdentifierV3Singleton = "reportV3Singleton"
        }

        init {
            categoryMapping[reportIdentifierV4] = ReportCategory.TIMESTAMP
            categoryMapping[reportIdentifierV3] = ReportCategory.GROUP_BY_DAY
            categoryMapping[reportIdentifierV3Singleton] = ReportCategory.SINGLETON
        }

        val nowVal: org.joda.time.DateTime get() = now()
        override fun now(): org.joda.time.DateTime {
            return org.joda.time.DateTime.parse("2018-11-12T0:00:00.000Z")
        }

        override fun studyStartDate(): org.joda.time.DateTime? {
            return org.joda.time.DateTime.parse("2018-11-06T0:00:00.00Z")
        }

        override fun defaultTimeZone(): ZoneId {
            return ZoneOffset.UTC  // makes unit tests consistent across device location
        }

        override fun subscribeCompletable(
                completable: Completable, successMsg: String, errorMsg: String) {
            completable.blockingGet()  // make the entire stream a blocking synchronous call
        }

        // Make a small page size for unit testing purposes (50 would require unnecessarily large json files)
        override val reportPageSizeV4: Int get() = 2

        // Make sure that even the async scheduler runs on the main threads
        override val asyncScheduler: Scheduler get() = AndroidSchedulers.mainThread()
    }
}