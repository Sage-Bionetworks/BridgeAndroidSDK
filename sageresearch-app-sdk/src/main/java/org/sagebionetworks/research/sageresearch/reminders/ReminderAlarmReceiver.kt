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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.support.annotation.VisibleForTesting
import android.support.v4.app.NotificationCompat
import org.researchstack.backbone.ui.MainActivity
import org.sagebionetworks.research.sageresearch.extensions.isBetweenInclusive
import org.sagebionetworks.research.sageresearch_app_sdk.R

import org.slf4j.LoggerFactory
import org.threeten.bp.LocalDateTime

/**
 * The ReminderAlarmReceiver can be sub-classed and used to easily control showing notifications to the user
 */
open class ReminderAlarmReceiver: BroadcastReceiver() {

    private val logger = LoggerFactory.getLogger(ReminderAlarmReceiver::class.java)

    /**
     * @return notificationChannelId which is set on Android OS >= 26, uniquely identified your notifications
     */
    protected fun notificationChannelId(context: Context): String {
        return context.getString(R.string.reminder_notification_channel_id)
    }

    /**
     * @return notificationChannelTitle which set on Android OS >= 26, explains your notifications
     */
    protected fun notificationChannelTitle(context: Context): String {
        return context.getString(R.string.reminder_notification_channel_title)
    }

    /**
     * @return notificationChannelDesc which is set on Android OS >= 26, explains your notifications in more detail
     */
    protected fun notificationChannelDesc(context: Context): String {
        return context.getString(R.string.reminder_notification_channel_desc)
    }

    /**
     * If true, when the notification is tapped, it will leave the user's status bar, false, it will stay there
     */
    protected open val shouldAutoCancel: Boolean get() = true

    @VisibleForTesting
    protected open val localDateTimeNow: LocalDateTime get() = LocalDateTime.now()

    /**
     * @param reminder associated with this sound
     * @param action of the notification
     * @return an alarm sound that plays when the notification appears in the status bar
     */
    protected open fun alarmSound(reminder: Reminder, action: String): Uri? {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /**
     * @param reminder associated with this icon
     * @param action of the notification
     * @return a drawable resource value of the small icon that shows with the notification
     */
    protected open fun notificationIcon(reminder: Reminder, action: String): Int? {
        return R.mipmap.ic_launcher
    }

    /**
     * @param will be the broadcast service context
     * @param code associated with this Activity to run when the notification is tapped
     * @param action of the notification
     * @return the pending intent which is run when the user taps the notification
     */
    protected open fun pendingIntent(
            context: Context, reminderManager: ReminderManager,
            reminder: Reminder, action: String): PendingIntent {

        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.action = action
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        notificationIntent.putExtra(REMINDER_JSON_KEY, reminderManager.jsonFromReminder(reminder))
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(MainActivity::class.java)
        stackBuilder.addNextIntent(notificationIntent)
        return stackBuilder.getPendingIntent(
                reminder.code, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Function called when the app receives a remote or local notification
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            logger.warn("Received broadcast with null context")
            return
        }

        if (intent == null) {
            logger.warn("Received broadcast with null intent")
            return
        }

        val action = intent.action

        // Collection reminder notification info
        val reminderManager = createReminderManager(context)
        val reminderJson = intent.getStringExtra(REMINDER_JSON_KEY)
        val reminder = reminderManager.reminderFromJson(reminderJson)

        if (reminder == null) {
            logger.error("Broadcast reminder action $action received but reminder extra is missing!")
            return
        }

        logger.info("Broadcast reminder action $action received with reminder info $reminder")
        // Trigger the notification
        if (!shouldIgnoreAlarm(reminder)) {
            showNotification(context, reminderManager, reminder, action)
        } else {
            logger.info("Reminder notification not being shown, today\'s date is in the ignore rules")
        }

        // Reschedule the reminders if it is a daily alarm
        if (reminder.reminderScheduleRules.isDailyAlarm) {
            val newReminder = recreateDailyReminder(reminder)
            reminderManager.scheduleReminder(context, newReminder)
        }

        if (reminder.isOneShotAlarm()) {
            reminderManager.cancelReminder(context, reminder)
        }
    }

    @VisibleForTesting
    open protected fun createReminderManager(context: Context): ReminderManager {
        return ReminderManager(context)
    }

    /**
     * Override to provide custom re-scheduling logic
     * @param reminder the reminder to re-schedule based on days that have passed
     */
    open fun recreateDailyReminder(reminder: Reminder): Reminder {
        val now = localDateTimeNow
        var newInitialAlarmTime = now
                .withHour(reminder.reminderScheduleRules.initialAlarmTime.hour)
                .withMinute(reminder.reminderScheduleRules.initialAlarmTime.minute)
                .withSecond(reminder.reminderScheduleRules.initialAlarmTime.second)
        if (newInitialAlarmTime.isBefore(now)) {
            newInitialAlarmTime = newInitialAlarmTime.plusDays(1)
        }
        val scheduleReminderRules = reminder.reminderScheduleRules.copy(initialAlarmTime = newInitialAlarmTime)
        val newReminder = reminder.copy(reminderScheduleRules = scheduleReminderRules)
        return newReminder
    }

    /**
     * Shows the notification to the user's status bar
     * @param context should be broadcast receiver context
     * @param reminder the reminder to show in the notification
     * @param action the notification action
     */
    protected fun showNotification(
            context: Context, reminderManager: ReminderManager,
            reminder: Reminder, action: String) {

        val notificationManager = (context.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager) ?: run {
            logger.warn("Could not obtain the notification manager service")
            return
        }

        // Register the notification channel (this is needed on Android 26 and greater)
        registerNotificationChannel(context, notificationManager)

        val builder = NotificationCompat.Builder(context, notificationChannelId(context))
        alarmSound(reminder, action)?.let {
            builder.setSound(it)
        }
        notificationIcon(reminder, action)?.let {
            builder.setSmallIcon(it)
        }
        builder.setAutoCancel(shouldAutoCancel)
        builder.setContentTitle(reminder.title)
        builder.setContentText(reminder.text)

        // The pending intent controls where the user goes after they tap the notification
        val pendingIntent = pendingIntent(context, reminderManager, reminder, action)
        builder.setContentIntent(pendingIntent)

        // Build and post the notification to the status bar
        val notification = builder.build()
        notificationManager.notify(reminder.code, notification)
    }

    protected fun registerNotificationChannel(context: Context, notificationManager: NotificationManager) {
        // Starting with API 26, notifications must be contained in a channel
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                    notificationChannelId(context),
                    notificationChannelTitle(context),
                    NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = notificationChannelDesc(context)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * @param reminder to be checked
     * @return true if the current date is within the reminder's ignore rule, false if alarm should not be ignored
     */
    open fun shouldIgnoreAlarm(reminder: Reminder): Boolean {
        val now = localDateTimeNow
        reminder.reminderScheduleRules.ignoreAlarmsRule?.let { ignoreRule ->
            var startDate = ignoreRule.startDateTime
            var endDate = ignoreRule.endDateTime
            if (now.isBetweenInclusive(startDate, endDate)) {
                return true
            }
            ignoreRule.repeatIntervalInDays?.let { repeatInDays ->
                var counter = 0
                while (now.isAfter(startDate)) {
                    counter += 1
                    if (now.isBetweenInclusive(startDate, endDate)) {
                        return true
                    }
                    startDate = ignoreRule.startDateTime.plusDays(repeatInDays * counter)
                    endDate = ignoreRule.endDateTime.plusDays(repeatInDays * counter)
                }
            }
        }
        return false
    }
}