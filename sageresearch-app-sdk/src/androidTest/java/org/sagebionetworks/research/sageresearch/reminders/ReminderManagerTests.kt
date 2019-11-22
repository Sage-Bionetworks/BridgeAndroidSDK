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

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.threeten.bp.LocalDateTime

class ReminderManagerTests {
    @Test
    fun scheduleReminder() {
        val context = InstrumentationRegistry.getTargetContext()
        val reminderManager = MockReminderManager(
                context)
        val reminder1 = Reminder("guid1", "action", 1,
                ReminderScheduleRules(LocalDateTime.now()), "title")

        reminderManager.scheduleReminder(context, reminder1)
        assertTrue(reminderManager.receiverEnabled)
        assertEquals(1, reminderManager.currentReminders().size)
        assertEquals(reminder1, reminderManager.currentReminders().firstOrNull())

        reminderManager.cancelReminder(context, reminder1)
        assertEquals(0, reminderManager.currentReminders().size)
        assertTrue(reminderManager.receiverEnabled)

        reminderManager.scheduleReminder(context, reminder1)
        assertTrue(reminderManager.receiverEnabled)
        assertEquals(1, reminderManager.currentReminders().size)
        assertEquals(reminder1, reminderManager.currentReminders().firstOrNull())

        reminderManager.cancelAllReminders(context)
        assertEquals(0, reminderManager.currentReminders().size)
        assertFalse(reminderManager.receiverEnabled)

        val reminder2 = Reminder("guid2", "action", 1,
                ReminderScheduleRules(LocalDateTime.now()), "title")

        reminderManager.scheduleReminder(context, reminder1)
        assertTrue(reminderManager.receiverEnabled)
        assertEquals(1, reminderManager.currentReminders().size)
        assertEquals(reminder1, reminderManager.currentReminders().firstOrNull())

        reminderManager.scheduleReminder(context, reminder2)
        assertTrue(reminderManager.receiverEnabled)
        assertEquals(2, reminderManager.currentReminders().size)
        assertEquals(reminder1, reminderManager.currentReminders().firstOrNull())
        assertEquals(reminder2, reminderManager.currentReminders().lastOrNull())

        reminderManager.cancelReminder(context, reminder1)
        assertEquals(1, reminderManager.currentReminders().size)
        assertTrue(reminderManager.receiverEnabled)

        reminderManager.cancelAllReminders(context)
        assertEquals(0, reminderManager.currentReminders().size)
        assertFalse(reminderManager.receiverEnabled)
    }

    class MockReminderManager(context: Context): ReminderManager(context) {
        fun currentReminders(): List<Reminder> {
            return super.allActiveReminders()
        }

        var receiverEnabled = false
        override fun enableReceiver(context: Context, enable: Boolean) {
            receiverEnabled = enable
        }
    }
}