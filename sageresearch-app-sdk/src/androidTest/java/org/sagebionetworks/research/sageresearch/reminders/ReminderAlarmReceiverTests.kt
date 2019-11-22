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

package org.sagebionetworks.research.sageresearch.reminders

import android.app.AlarmManager.INTERVAL_DAY
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.threeten.bp.LocalDateTime

class ReminderAlarmReceiverTests {

    @Test
    fun test_ignoreAlarmRules() {
        val context = InstrumentationRegistry.getTargetContext()
        val receiver = MockAlarmReceiver(
                MockReminderManager(
                        context))

        val ignoreRules = ReminderScheduleIgnoreRule(
                LocalDateTime.of(2018, 10, 25, 0, 0), // start
                LocalDateTime.of(2018, 10, 30, 0, 0), // end
                14L)

        val initialReminderTime = LocalDateTime.of(2018, 10, 10, 11, 0)
        val reminder = Reminder("guid", "action", 1,
                ReminderScheduleRules(initialReminderTime, null, ignoreRules), "title")

        receiver.currentDateTime = LocalDateTime.of(2018, 10, 10, 11, 0)
        assertFalse(receiver.shouldIgnoreAlarm(reminder))

        receiver.currentDateTime = LocalDateTime.of(2018, 10, 25, 11, 0)
        assertTrue(receiver.shouldIgnoreAlarm(reminder))

        receiver.currentDateTime = LocalDateTime.of(2018, 10, 29, 23, 59)
        assertTrue(receiver.shouldIgnoreAlarm(reminder))

        receiver.currentDateTime = LocalDateTime.of(2018, 10, 31, 0, 1)
        assertFalse(receiver.shouldIgnoreAlarm(reminder))

        // Ignore rules now repeat to Nov 8
        receiver.currentDateTime = LocalDateTime.of(2018, 11, 7, 11, 0)
        assertFalse(receiver.shouldIgnoreAlarm(reminder))

        receiver.currentDateTime = LocalDateTime.of(2018, 11, 8, 11, 0)
        assertTrue(receiver.shouldIgnoreAlarm(reminder))

        receiver.currentDateTime = LocalDateTime.of(2018, 11, 12, 23, 59)
        assertTrue(receiver.shouldIgnoreAlarm(reminder))

        receiver.currentDateTime = LocalDateTime.of(2018, 11, 13, 11, 0)
        assertFalse(receiver.shouldIgnoreAlarm(reminder))
    }

    @Test
    fun test_isOneShotAlarm() {
        var reminder = Reminder("guid", "action", 1,
                ReminderScheduleRules(LocalDateTime.now(), null), "title")
        assertTrue(reminder.isOneShotAlarm())

        reminder = Reminder("guid", "action", 1,
                ReminderScheduleRules(LocalDateTime.now(), 1000L), "title")
        assertFalse(reminder.isOneShotAlarm())

        reminder = Reminder("guid", "action", 1,
                ReminderScheduleRules(LocalDateTime.now(),
                        null, null, true), "title")
        assertFalse(reminder.isOneShotAlarm())

        reminder = Reminder("guid", "action", 1,
                ReminderScheduleRules(LocalDateTime.now(),
                        1000L, null, true), "title")
        assertFalse(reminder.isOneShotAlarm())
    }

    @Test
    fun test_rescheduleDailyReminder() {
        val context = InstrumentationRegistry.getTargetContext()
        val reminderManager = MockReminderManager(
                context)
        val receiver = MockAlarmReceiver(
                reminderManager)

        val ignoreRules = ReminderScheduleIgnoreRule(
                LocalDateTime.of(2018, 10, 25, 0, 0), // start
                LocalDateTime.of(2018, 10, 30, 0, 0), // end
                14L)
        val initialReminderTime = LocalDateTime.of(2018, 10, 9, 11, 0)
        val dailyScheduleRules = ReminderScheduleRules(initialReminderTime, INTERVAL_DAY, ignoreRules, true)
        var dailyReminder = Reminder("guid", "action", 1, dailyScheduleRules, "title", "text")

        receiver.currentDateTime = LocalDateTime.of(2018, 10, 10, 12, 0)
        var newReminder = receiver.recreateDailyReminder(dailyReminder)
        // The reminder should be the same except for the initial reminder time
        assertEquals("guid", newReminder.guid)
        assertEquals(1, newReminder.code)
        assertEquals("action", newReminder.action)
        assertEquals("title", newReminder.title)
        assertEquals("text", newReminder.text)
        assertEquals(ignoreRules, newReminder.reminderScheduleRules.ignoreAlarmsRule)
        assertEquals(INTERVAL_DAY, newReminder.reminderScheduleRules.repeatAlarmInterval)
        assertEquals(true, newReminder.reminderScheduleRules.isDailyAlarm)
        // The reminder should have an initial time set to the next schedule the day after current time
        assertEquals(LocalDateTime.of(2018, 10, 11, 11, 0),
                newReminder.reminderScheduleRules.initialAlarmTime)

        receiver.currentDateTime = LocalDateTime.of(2018, 10, 12, 10, 0)
        newReminder = receiver.recreateDailyReminder(dailyReminder)
        // The reminder should have an initial time set to the next schedule the day of current time
        assertEquals(LocalDateTime.of(2018, 10, 12, 11, 0),
                newReminder.reminderScheduleRules.initialAlarmTime)
    }

    class MockAlarmReceiver(private val reminderManager: MockReminderManager): ReminderAlarmReceiver() {
        var currentDateTime: LocalDateTime = LocalDateTime.now()
        override val localDateTimeNow: LocalDateTime get() = currentDateTime

        override fun createReminderManager(context: Context): ReminderManager {
            return reminderManager
        }
    }

    class MockReminderManager(context: Context): ReminderManager(context) {
        fun currentReminders(): List<Reminder> {
            return super.allActiveReminders()
        }
    }
}