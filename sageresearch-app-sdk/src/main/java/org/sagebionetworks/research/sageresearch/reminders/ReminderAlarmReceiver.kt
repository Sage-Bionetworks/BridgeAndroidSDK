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
import android.support.v4.app.NotificationCompat
import org.researchstack.backbone.ui.MainActivity
import org.sagebionetworks.research.sageresearch_app_sdk.R

import org.slf4j.LoggerFactory

/**
 * The ReminderAlarmReceiver can be sub-classed and used to easily control showing notifications to the user
 */
open class ReminderAlarmReceiver: BroadcastReceiver() {

    private val logger = LoggerFactory.getLogger(ReminderAlarmReceiver::class.java)

    /**
     * @property notificationChannelId set on Android OS >= 26, uniquely identified your notifications
     */
    protected open val notificationChannelId: String
        get() {
            return "Sage-Research NotificationChannelId"
        }

    /**
     * @property notificationChannelId set on Android OS >= 26, explains your notifications
     */
    protected open val notificationChannelTitle: String
        get() {
            return "Sage-Research Reminders"
        }

    /**
     * @property notificationChannelId set on Android OS >= 26, explains your notifications in more detail
     */
    protected open val notificationChannelDesc: String
        get() {
            return "SageResearch reminders help you remember to log your data."
        }

    /**
     * If true, when the notification is tapped, it will leave the user's status bar, false, it will stay there
     */
    protected open val shouldAutoCancel: Boolean
        get() {
            return true
        }

    /**
     * @param code associated with this sound
     * @param action of the notification
     * @return an alarm sound that plays when the notification appears in the status bar
     */
    protected open fun alarmSound(code: Int, action: String): Uri? {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /**
     * @param code associated with this icon
     * @param action of the notification
     * @return a drawable resource value of the small icon that shows with the notification
     */
    protected open fun notificationIcon(code: Int, action: String): Int? {
        return R.mipmap.ic_launcher
    }

    /**
     * @param will be the broadcast service context
     * @param code associated with this Activity to run when the notification is tapped
     * @param action of the notification
     * @return the pending intent which is run when the user taps the notification
     */
    protected open fun pendingIntent(context: Context, code: Int, action: String): PendingIntent {
        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.action = action
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(MainActivity::class.java)
        stackBuilder.addNextIntent(notificationIntent)
        return stackBuilder.getPendingIntent(
                code, PendingIntent.FLAG_UPDATE_CURRENT)
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

        // Collection reminder notification info
        val code = intent.getIntExtra(REMINDER_KEY_CODE, -1)
        val title = intent.getStringExtra(REMINDER_KEY_TITLE)
        val text = intent.getStringExtra(REMINDER_KEY_TEXT)
        val action = intent.action

        logger.info("Broadcast reminder received with code $code, " +
                "title $title, text $text, and action $action")

        // Trigger the notification
        showNotification(context, code, title, text, intent.action)
    }

    /**
     * Shows the notification to the user's status bar
     * @param context should be broadcast receiver context
     * @param code the notification code
     * @param title the title to show in the notification
     * @param text the text to show in the notification
     * @param action the notification action
     */
    protected fun showNotification(
            context: Context,
            code: Int,
            title: String?,
            text: String?,
            action: String) {

        val notificationManager = (context.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager) ?: run {
            logger.warn("Could not obtain the notification manager service")
            return
        }

        // Register the notification channel (this is needed on Android 26 and greater)
        registerNotificationChannel(notificationManager)

        val builder = NotificationCompat.Builder(context, notificationChannelId)
        alarmSound(code, action)?.let {
            builder.setSound(it)
        }
        notificationIcon(code, action)?.let {
            builder.setSmallIcon(it)
        }
        builder.setAutoCancel(shouldAutoCancel)
        builder.setContentTitle(title)
        builder.setContentText(text)

        // The pending intent controls where the user goes after they tap the notification
        val pendingIntent = pendingIntent(context, code, action)
        builder.setContentIntent(pendingIntent)

        // Build and post the notification to the status bar
        val notification = builder.build()
        notificationManager.notify(code, notification)
    }

    protected fun registerNotificationChannel(notificationManager: NotificationManager) {
        // Starting with API 26, notifications must be contained in a channel
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                    notificationChannelId,
                    notificationChannelTitle,
                    NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = notificationChannelDesc
            notificationManager.createNotificationChannel(channel)
        }
    }
}