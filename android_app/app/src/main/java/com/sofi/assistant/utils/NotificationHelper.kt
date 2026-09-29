package com.sofi.assistant.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sofi.assistant.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val SERVICE_CHANNEL_ID = "sofi_foreground_service"
        const val IMPORTANT_CHANNEL_ID = "sofi_important_messages"
        const val CALL_CHANNEL_ID = "sofi_call_alerts"
        const val SERVICE_NOTIFICATION_ID = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Sofi Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status of Sofi AI Call & SMS automation"
                setSound(null, null)
            }

            val importantChannel = NotificationChannel(
                IMPORTANT_CHANNEL_ID,
                "Important Message Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for high priority incoming messages and calls"
                enableVibration(true)
                enableLights(true)
            }

            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Sofi Auto-Attended Call Voicemails",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pop-up alerts for auto-attended caller messages"
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(importantChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    fun buildServiceNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("Sofi AI Assistant Active")
            .setContentText("Auto-Call Attend (10s) & Phone Control Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showImportantMessageNotification(sender: String, messageText: String, reason: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, IMPORTANT_CHANNEL_ID)
            .setContentTitle("🚨 URGENT: Message from $sender")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$messageText\n\n[Sofi Note: $reason]"))
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showCallerMessagePopup(callerNumber: String, messageText: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setContentTitle("📞 Sofi Auto-Attended Call ($callerNumber)")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Caller $callerNumber left message:\n\"$messageText\""))
            .setSmallIcon(android.R.drawable.stat_notify_call_mute)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(pendingIntent, true) // High priority pop-up banner
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
