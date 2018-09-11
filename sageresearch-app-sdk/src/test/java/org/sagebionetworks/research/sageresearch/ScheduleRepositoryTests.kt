package org.sagebionetworks.research.sageresearch

import junit.framework.Assert.assertEquals

import org.joda.time.DateTime
import org.junit.Test

import org.sagebionetworks.research.sageresearch.viewmodel.ScheduleRepositoryHelper

class ScheduleRepositoryTests {

    // No need to test any value but 14, because that is currently a bridge limitation
    val maxRequestDays = 14

    @Test
    fun requestMap_LessThan14Days() {
        val start = DateTime.parse("2018-08-17T00:00:00.000-04:00")
        val end = DateTime.parse("2018-08-27T00:00:00.000-04:00")
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(start, end, maxRequestDays)
        assertEquals(1, requestMap.keys.size)
        assertEquals(DateTime.parse("2018-08-17T00:00:00.000-04:00"), requestMap.keys.elementAt(0))
        assertEquals(DateTime.parse("2018-08-27T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(0)])
    }

    @Test
    fun requestMap_MoreThan14DaysEven() {
        val start = DateTime.parse("2018-08-18T12:00:00.000-04:00")
        val end = DateTime.parse("2018-09-14T00:12:00.000-04:00")
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(start, end, maxRequestDays)
        assertEquals(2, requestMap.keys.size)
        assertEquals(DateTime.parse("2018-09-01T00:00:00.000-04:00"), requestMap.keys.elementAt(0))
        assertEquals(DateTime.parse("2018-09-14T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(0)])
        assertEquals(DateTime.parse("2018-08-18T00:00:00.000-04:00"), requestMap.keys.elementAt(1))
        assertEquals(DateTime.parse("2018-08-31T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(1)])
    }

    @Test
    fun requestMap_MoreThan14DaysRemainder() {
        val start = DateTime.parse("2018-08-17T10:00:00.000-04:00")
        val end = DateTime.parse("2018-09-09T10:00:00.000-04:00")
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(start, end, maxRequestDays)
        assertEquals(2, requestMap.keys.size)
        assertEquals(DateTime.parse("2018-08-27T00:00:00.000-04:00"), requestMap.keys.elementAt(0))
        assertEquals(DateTime.parse("2018-09-09T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(0)])
        assertEquals(DateTime.parse("2018-08-17T00:00:00.000-04:00"), requestMap.keys.elementAt(1))
        assertEquals(DateTime.parse("2018-08-26T23:59:59.999-04:00"), requestMap[requestMap.keys.elementAt(1)])
    }
}