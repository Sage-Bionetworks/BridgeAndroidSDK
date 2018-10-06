package org.sagebionetworks.bridge.researchstack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.joda.time.DateTime;
import org.junit.Test;

public class DateTimeUtilsTest {
    @Test
    public void parseDateTime_Null() {
        DateTime result = DateTimeUtils.parseDateTime(null);
        assertNull(result);
    }

    @Test
    public void parseDateTime_Empty() {
        DateTime result = DateTimeUtils.parseDateTime("");
        assertNull(result);
    }

    @Test
    public void parseDateTime_Invalid() {
        DateTime result = DateTimeUtils.parseDateTime("Thursday November 2, 2017");
        assertNull(result);
    }

    @Test
    public void parseDateTime_Success() {
        // Use a non-UTC non-PST timezone to verify that we're parsing timezone correctly.
        DateTime result = DateTimeUtils.parseDateTime("2017-11-02T14:28:53.451+0900");
        assertNotNull(result);
        assertEquals(2017, result.getYear());
        assertEquals(11, result.getMonthOfYear());
        assertEquals(2, result.getDayOfMonth());
        assertEquals(14, result.getHourOfDay());
        assertEquals(28, result.getMinuteOfHour());
        assertEquals(53, result.getSecondOfMinute());
        assertEquals(451, result.getMillisOfSecond());
        // Offset is in milliseconds
        assertEquals(9 * 3600 * 1000, result.getZone().getOffset(result));
    }
}
