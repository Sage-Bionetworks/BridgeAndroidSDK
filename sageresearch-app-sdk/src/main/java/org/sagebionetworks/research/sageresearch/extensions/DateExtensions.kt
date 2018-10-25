package org.sagebionetworks.research.sageresearch.extensions

import org.joda.time.DateTime
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime

//
//  Copyright © 2018 Sage Bionetworks. All rights reserved.
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

fun LocalDateTime.startOfDay(): LocalDateTime {
    return this.toLocalDate().atStartOfDay()
}

fun LocalDateTime.endOfDay(): LocalDateTime {
    return this.startOfDay().plusDays(1).minusNanos(1)
}

fun LocalDateTime.startOfNextDay(): LocalDateTime {
    return this
            .plusDays(1)
            .startOfDay()
}

fun LocalDateTime.inSameDayAs(date: LocalDateTime): Boolean {
    return this.isBetweenInclusive(date.startOfDay(), date.endOfDay())
}

fun LocalDateTime.isBetweenInclusive(start: LocalDateTime, end: LocalDateTime): Boolean {
    return this == start || this == end ||
            (this.isAfter(start) && this.isBefore(end))
}

fun LocalDateTime.toInstant(timezone: ZoneId): Instant {
    return this.atZone(timezone).toInstant()
}

fun ZonedDateTime.startOfDay(): ZonedDateTime {
    return this.toLocalDate().atStartOfDay(this.zone)
}

fun ZonedDateTime.endOfDay(): ZonedDateTime {
    return this.startOfDay().plusDays(1).minusNanos(1)
}

fun ZonedDateTime.startOfNextDay(): ZonedDateTime {
    return this
            .plusDays(1)
            .startOfDay()
}

fun Instant.inSameDayAs(date: LocalDateTime, timezone: ZoneId): Boolean {
    return this.isBetweenInclusive(date.startOfDay(), date.endOfDay(), timezone)
}

fun Instant.isBetweenInclusive(start: LocalDateTime, end: LocalDateTime, timezone: ZoneId): Boolean {
    val startZoned = start.atZone(timezone).toInstant()
    val endZoned = end.atZone(timezone).toInstant()
    return this == startZoned || this == endZoned ||
            (this.isAfter(startZoned) && this.isBefore(endZoned))
}

fun LocalDateTime.toJodaDateTime(): org.joda.time.DateTime {
    // TODO: mdephillips 10/14/18 better way to convert from 3tenbp LocalDateTime to jodatime DateTime?
    return DateTime.now()
            .withYear(this.year)
            .withDayOfYear(this.dayOfYear)
            .withHourOfDay(this.hour)
            .withMinuteOfHour(this.minute)
            .withSecondOfMinute(this.second)
}

fun org.joda.time.DateTime.toThreeTenLocalDateTime(): LocalDateTime {
    // TODO: mdephillips 10/14/18 better way to convert from jodatime DateTime 3tenbp LocalDateTime?
    return LocalDateTime.now()
            .withYear(this.year)
            .withDayOfYear(this.dayOfYear)
            .withHour(this.hourOfDay)
            .withMinute(this.minuteOfHour)
            .withSecond(this.secondOfMinute)
}