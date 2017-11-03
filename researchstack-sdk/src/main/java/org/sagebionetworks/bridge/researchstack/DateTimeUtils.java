package org.sagebionetworks.bridge.researchstack;

import android.support.annotation.Nullable;
import com.google.common.base.Strings;
import org.joda.time.DateTime;

/**
 * Contains utility functions for handling Joda DateTimes.
 */
public class DateTimeUtils {
    /**
     * Given an ISO8601 date-time string (YYYY-MM-DDThh:mm:ss.ssszzzzzz), parses it into a Joda
     * DateTime. If the string is null, empty, or otherwise cannot be parsed into a DateTime, this
     * method returns null.
     *
     * @param dateTimeStr an ISO8601 date-time string
     * @return Joda DateTime, or null if the string couldn't be parsed
     */
    @Nullable
    public static DateTime parseDateTime(String dateTimeStr) {
        if (Strings.isNullOrEmpty(dateTimeStr)) {
            return null;
        }

        try {
            return DateTime.parse(dateTimeStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
