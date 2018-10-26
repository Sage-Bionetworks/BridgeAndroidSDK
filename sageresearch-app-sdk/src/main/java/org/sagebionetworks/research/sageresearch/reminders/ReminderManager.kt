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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.support.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.slf4j.LoggerFactory
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Used to pass key/values through intent data
 */
const val REMINDER_JSON_KEY = "REMINDER_DATA_CLASS"

/**
 * ReminderManager controls the scheduling and cancellation of the reminder alarms
 */
open class ReminderManager(context: Context) {

    private val logger = LoggerFactory.getLogger(ReminderManager::class.java)

    /**
     * @property gson used to deserialize Reminder objects
     */
    protected val gson = Gson()

    /**
     * @property reminderAlarmReceiver the receiver to associate with this manager
     */
    open val reminderAlarmReceiver: Class<*>
        get() {
            return ReminderAlarmReceiver::class.java
        }

    /**
     * @property sharedPrefs used to read/write PendingIntent vars
     */
    protected var sharedPrefs: SharedPreferences =
            context.getSharedPreferences("ReminderManagerPendingIntents", MODE_PRIVATE)

    /**
     * @param context can be app, activity, or service
     * @return reminders that are currently scheduled to be shown
     */
    protected fun allActiveReminders(): List<Reminder> {
        return sharedPrefs.all.mapNotNull {
            (it.value as? String)?.let { reminderJson ->
                return@mapNotNull reminderFromJson(reminderJson)
            } ?: run {
                logger.warn("Invalid reminder value with guid ${it.key}")
            }
            return@mapNotNull null
        }
    }

    /**
     * Converts a reminder json string to a Reminder
     * @param reminderJson formed by serializing a Reminder object with Gson
     * @return reminder formed from reminderJson using Gson
     */
    fun reminderFromJson(reminderJson: String?): Reminder? {
        reminderJson?.let {
            try {
                return gson.fromJson(it, Reminder::class.java)
            } catch (e: JsonSyntaxException) {
                logger.error(e.localizedMessage)
            }
        }
        return null
    }

    /**
     * @param reminder to be serialized into a json string
     * @return json string from a reminder object using default gson
     */
    fun jsonFromReminder(reminder: Reminder): String {
        return gson.toJson(reminder)
    }

    /**
     * For reminder to be canceled, each reminder must produce the same PendingIntent each time
     * @param context can be app or activity
     * @param reminder to be turned into a PendingIntent
     * @return a pending intent for the reminder data class
     */
    protected fun pendingIntentForReminder(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, reminderAlarmReceiver)
        intent.action = reminder.action
        intent.putExtra(REMINDER_JSON_KEY, jsonFromReminder(reminder))
        intent.data = Uri.parse(reminder.guid)
        return PendingIntent.getBroadcast(context, reminder.code, intent, PendingIntent.FLAG_ONE_SHOT)
    }

    /**
     * @param context used to schedule the alarm for the local notification
     * @param type the type of reminder
     */
    fun scheduleReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: run {
            logger.warn("Could not obtain alarm service to schedule reminder")
            return
        }

        // Enable receiver so it can intercept alarm broadcasts
        enableReceiver(context, true)
        // Cancel any previously scheduled reminders that have the same pending intent
        cancelReminder(context, reminder)
        val pendingIntent = pendingIntentForReminder(context, reminder)

        // Persist the reminder info so we can cancel it later if we need to
        val reminderJson = jsonFromReminder(reminder)
        sharedPrefs.edit().putString(reminder.guid, reminderJson).apply()

        val initialAlarmDateTime = reminder.reminderScheduleRules.initialAlarmTime
        val initialAlarmEpochMillis = TimeUnit.SECONDS.toMillis(
                initialAlarmDateTime.atZone(ZoneId.systemDefault()).toEpochSecond())

        reminder.reminderScheduleRules.repeatAlarmInterval?.let {
            logger.info("Setting repeat reminder with info $reminder")
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, initialAlarmEpochMillis, it, pendingIntent)
        } ?: run {
            logger.info("Setting reminder with info $reminder")
            alarmManager.set(AlarmManager.RTC_WAKEUP, initialAlarmEpochMillis, pendingIntent)
        }
    }

    /**
     * Cancels a single reminder
     * @param context can be app or activity
     * @param reminder to cancel
     */
    fun cancelReminder(context: Context, reminder: Reminder) {
        (context.getSystemService(ALARM_SERVICE) as? AlarmManager)?.let { alarmManager ->
            // Let's check for a previously scheduled reminder with the same guid
            // Some Reminder data may have changed, but if it has the same guid it should always be canceled
            sharedPrefs.getString(reminder.guid, null)?.let { reminderJson ->
                reminderFromJson(reminderJson)?.let {
                    val pendingIntent = pendingIntentForReminder(context, it)
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
            // Always cancel the reminder as it currently exists as well
            val pendingIntent = pendingIntentForReminder(context, reminder)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            sharedPrefs.edit().remove(reminder.guid).apply()
        } ?: run {
            logger.warn("Failed to obtain alarm service to cancel all reminders")
        }
    }

    /**
     * Cancels all currently scheduled reminders
     * This only works if @property allActivePendingIntents returns identical PendingIntents
     * to the ones used when the notification was originally scheduled
     * @context can be app or activity
     * @return the list of reminders that were cancelled
     */
    fun cancelAllReminders(context: Context): List<Reminder> {
        enableReceiver(context, false)
        val reminderList = ArrayList<Reminder>()
        (context.getSystemService(ALARM_SERVICE) as? AlarmManager)?.let { alarmManager ->
            allActiveReminders().map {
                cancelReminder(context, it)
                reminderList.add(it)
            }
        } ?: run {
            logger.warn("Failed to obtain alarm service to cancel all reminders")
        }
        return reminderList
    }

    /**
     * @context can be app or activity
     * @param enable if true, the broadcast receiver will be enabled,
     *               if false, it will be disabled and it won't receive existing pending intents
     */
    @VisibleForTesting
    protected open fun enableReceiver(context: Context, enable: Boolean) {
        val state =
            if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val receiver = ComponentName(context, reminderAlarmReceiver)
        val pm = context.packageManager
        pm.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP)
    }
}

/**
 * Reminder encapsulates the setup of an alarm notification to be used by the ReminderManager
 */
data class Reminder(
    /**
     * @property guid uniquely identifying this reminder
     */
    val guid: String,
    /**
     * @property action notification action
     */
    val action: String,
    /**
     * @property code notification code
     */
    val code: Int,
    /**
     * @property reminderScheduleRules determines the timing and frequency of scheduling the reminder alarms
     */
    val reminderScheduleRules: ReminderScheduleRules,
    /**
     * @property title that will show on the notification
     */
    val title: String,
    /**
     * @property text more details that will show on the notification
     */
    val text: String? = null)

/**
 * ReminderScheduleRules is built to contain info about how to schedule alarms, and rules about
 * if they should be ignored or if they should show a notification
 */
data class ReminderScheduleRules(
    /**
     * @property initialAlarmTime time in milliseconds when the initial alarm will fire
     */
    val initialAlarmTime: LocalDateTime,
    /**
     * @property repeatAlarmInterval if non-null, will be used to schedule repeated alarms,
     *                               if null, alarm only fires once
     */
    val repeatAlarmInterval: Long? = null,
    /**
     * @property ignoreAlarmsRule to be used to ignore showing notifications for set periods of complex scheduling
     */
    val ignoreAlarmsRule: ReminderScheduleIgnoreRule? = null,
    /**
     * @property isDailyAlarm If true, this reminder will be rescheduled every time its broadcast
     *                        receiver is called so that the daily LocalDateTime is as accurate as possible.
     *                        If false, no automatic rescheduling will be done.
     *                        This may be useful if the notification needs to be triggered at a certain
     *                        time of day, every day.  Otherwise, DST would throw off the repeat interval,
     *                        which has to be in milliseconds.
     */
    val isDailyAlarm: Boolean = false)

/**
 * ReminderScheduleIgnoreRule is to be used to ignore set periods of alarms for complex schedules
 */
data class ReminderScheduleIgnoreRule(
    /**
     * @property startDateTime of the period to ignore showing an alarm notification
     */
    val startDateTime: LocalDateTime,
    /**
     * @property endDateTime of the period to ignore showing an alarm notification
     */
    val endDateTime: LocalDateTime,
    /**
     * @property repeatIntervalInDays the range can be repeated at an interval to achieve complex ignore logic
     *                                for example, if ignoring 10/10 - 10/20, and repeat is 20, then
     *                                10/30 - 11/9 will also be ignored, and so forth
     */
    val repeatIntervalInDays: Long? = null)

/**
 * @return true if the alarm should only fire once, and should be cancelled after it has
 */
fun Reminder.isOneShotAlarm(): Boolean {
    return !this.reminderScheduleRules.isDailyAlarm &&
            this.reminderScheduleRules.repeatAlarmInterval == null
}