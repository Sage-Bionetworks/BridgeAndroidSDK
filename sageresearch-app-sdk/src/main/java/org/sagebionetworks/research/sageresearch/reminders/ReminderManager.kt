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
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.slf4j.LoggerFactory

/**
 * Used to pass key/values through intent data
 */
const val REMINDER_KEY_GUID     = "KEY_GUID"
const val REMINDER_KEY_CODE     = "KEY_CODE"
const val REMINDER_KEY_TITLE    = "KEY_TITLE"
const val REMINDER_KEY_TEXT     = "KEY_TEXT"

/**
 * ReminderManager controls the scheduling and cancellation of the reminder alarms
 */
open class ReminderManager {

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
     * @property allActivePendingIntents active pending intents must always have the same value for
     *                                   notifications that are currently scheduled to be properly canceled
     */
    protected fun allActivePendingIntents(context: Context): List<PendingIntent> {
        return allActiveReminders(context).map {
            pendingIntentForReminder(context, it)
        }
    }

    /**
     * @param context can be app, activity, or service
     * @return reminders that are currently scheduled to be shown
     */
    protected fun allActiveReminders(context: Context): List<Reminder> {
        return pendingIntentPrefs(context).all.mapNotNull {
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
    protected fun reminderFromJson(reminderJson: String): Reminder? {
        try {
            return gson.fromJson(reminderJson, Reminder::class.java)
        } catch (e: JsonSyntaxException) {
            logger.warn(e.localizedMessage)
            return null
        }
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
        intent.putExtra(REMINDER_KEY_GUID, reminder.guid)
        intent.putExtra(REMINDER_KEY_CODE, reminder.code)
        intent.putExtra(REMINDER_KEY_TITLE, reminder.title)
        intent.putExtra(REMINDER_KEY_TEXT, reminder.text)
        intent.data = Uri.parse(reminder.guid)
        return PendingIntent.getBroadcast(context, reminder.code, intent, PendingIntent.FLAG_ONE_SHOT)
    }

    /**
     * @param context can be app or activity
     * @return a shared preferences object used to store PendingIntents
     */
    protected fun pendingIntentPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("ReminderManagerPendingIntents", MODE_PRIVATE)
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
        val reminderJson = gson.toJson(reminder)
        pendingIntentPrefs(context).edit().putString(reminder.guid, reminderJson).apply()

        reminder.repeatAlarmInterval?.let {
            logger.info("Setting repeat reminder with info $reminder")
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, reminder.alarmEpochMillis, it, pendingIntent)
        } ?: run {
            logger.info("Setting reminder with info $reminder")
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.alarmEpochMillis, pendingIntent)
        }
    }

    /**
     * Cancels a single reminder
     * @param context can be app or activity
     * @param reminder to cancel
     */
    fun cancelReminder(context: Context, reminder: Reminder) {
        (context.getSystemService(ALARM_SERVICE) as? AlarmManager)?.let { alarmManager ->
            val pendingIntent = pendingIntentForReminder(context, reminder)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        } ?: run {
            logger.warn("Failed to obtain alarm service to cancel all reminders")
        }
    }

    /**
     * Cancels all currently scheduled reminders
     * This only works if @property allActivePendingIntents returns identical PendingIntents
     * to the ones used when the notification was originally scheduled
     * @context can be app or activity
     */
    fun cancelAllReminders(context: Context) {
        enableReceiver(context, false)
        (context.getSystemService(ALARM_SERVICE) as? AlarmManager)?.let { alarmManager ->
            allActivePendingIntents(context).forEach {
                alarmManager.cancel(it)
                it.cancel()
            }
        } ?: run {
            logger.warn("Failed to obtain alarm service to cancel all reminders")
        }
    }

    /**
     * @context can be app or activity
     * @param enable if true, the broadcast receiver will be enabled,
     *               if false, it will be disabled and it won't receive existing pending intents
     */
    protected fun enableReceiver(context: Context, enable: Boolean) {
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
     * @property alarmEpochMillis time in milliseconds when the initial alarm will fire
     */
    val alarmEpochMillis: Long,
    /**
     * @property repeatAlarmInterval if non-null, will be used to schedule repeated alarms,
     *                               if null, alarm only fires once
     */
    val repeatAlarmInterval: Long? = null,
    /**
     * @property title that will show on the notification
     */
    val title: String,
    /**
     * @property text more details that will show on the notification
     */
    val text: String? = null)