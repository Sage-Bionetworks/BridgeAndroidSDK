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
        assertEquals(requestMap.keys.elementAt(0), start)
        assertEquals(requestMap[requestMap.keys.elementAt(0)], 10)
    }

    @Test
    fun requestMap_MoreThan14DaysEven() {
        val start = DateTime.parse("2018-08-17T00:00:00.000-04:00")
        val end = DateTime.parse("2018-09-14T00:00:00.000-04:00")
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(start, end, maxRequestDays)
        assertEquals(2, requestMap.keys.size)
        assertEquals(start, requestMap.keys.elementAt(0))
        assertEquals(14, requestMap[requestMap.keys.elementAt(0)])
        assertEquals(start.plusDays(14), requestMap.keys.elementAt(1))
        assertEquals(14, requestMap[requestMap.keys.elementAt(1)])
    }

    @Test
    fun requestMap_MoreThan14DaysRemainder() {
        val start = DateTime.parse("2018-08-17T00:00:00.000-04:00")
        val end = DateTime.parse("2018-09-09T00:00:00.000-04:00")
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(start, end, maxRequestDays)
        assertEquals(2, requestMap.keys.size)
        assertEquals(start, requestMap.keys.elementAt(0))
        assertEquals(14, requestMap[requestMap.keys.elementAt(0)])
        assertEquals(start.plusDays(14), requestMap.keys.elementAt(1))
        assertEquals(9, requestMap[requestMap.keys.elementAt(1)])
    }
}