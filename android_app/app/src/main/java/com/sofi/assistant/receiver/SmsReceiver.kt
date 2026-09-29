package com.sofi.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import com.sofi.assistant.ai.SofiAiEngine
import com.sofi.assistant.utils.NotificationHelper
import com.sofi.assistant.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val prefs = PreferencesManager(context)
        val aiEngine = SofiAiEngine(context)
        val notificationHelper = NotificationHelper(context)

        for (sms in messages) {
            val sender = sms.originatingAddress ?: "Unknown"
            val body = sms.messageBody ?: continue

            prefs.addActivityLog("Received SMS from $sender: \"$body\"")

            // Process asynchronously in background thread
            CoroutineScope(Dispatchers.IO).launch {
                // 1. Check if Important / Urgent Message
                if (prefs.isImportantNotifEnabled) {
                    val (isImportant, reason) = aiEngine.checkIsImportantMessage(sender, body)
                    if (isImportant) {
                        prefs.addActivityLog("🚨 Urgent SMS Flagged from $sender ($reason)")
                        notificationHelper.showImportantMessageNotification(sender, body, reason)
                    }
                }

                // 2. Auto-Reply to SMS
                if (prefs.isAutoSmsEnabled) {
                    try {
                        val replyText = aiEngine.generateSmsAutoReply(sender, body)
                        sendSmsReply(sender, replyText)
                        prefs.addActivityLog("Sofi Auto-Replied SMS to $sender: \"$replyText\"")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        prefs.addActivityLog("Failed to send auto-reply SMS to $sender: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    private fun sendSmsReply(recipient: String, replyMessage: String) {
        val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        smsManager.sendTextMessage(recipient, null, replyMessage, null, null)
    }
}
