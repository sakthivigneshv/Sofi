package com.sofi.assistant.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.sofi.assistant.service.SofiInCallService
import com.sofi.assistant.utils.NotificationHelper
import com.sofi.assistant.utils.PreferencesManager
import java.util.Locale

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var autoAnswerHandler: Handler? = null
        private var autoAnswerRunnable: Runnable? = null
        private var isRinging = false
        private var currentCallerNumber: String? = null
        private var currentCallerName: String? = null
        private var ttsEngine: TextToSpeech? = null
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val prefs = PreferencesManager(context)

        if (!prefs.isAutoCallEnabled) return

        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!incomingNumber.isNullOrEmpty()) {
            currentCallerNumber = incomingNumber
            currentCallerName = getContactName(context, incomingNumber)
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                isRinging = true
                val delayMs = (prefs.callAnswerDelaySeconds.coerceAtLeast(1)) * 1000L
                val callerLabel = currentCallerName ?: currentCallerNumber ?: "Unknown Caller"
                prefs.addActivityLog("Incoming call from $callerLabel. Sofi auto-attend scheduled in ${delayMs / 1000}s")

                cancelPendingTimer()

                autoAnswerHandler = Handler(Looper.getMainLooper())
                autoAnswerRunnable = Runnable {
                    if (isRinging) {
                        prefs.addActivityLog("10s elapsed! Sofi automatically attending call from $callerLabel")
                        answerIncomingCall(context, prefs)
                    }
                }
                autoAnswerHandler?.postDelayed(autoAnswerRunnable!!, delayMs)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (isRinging) {
                    isRinging = false
                    cancelPendingTimer()
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                isRinging = false
                cancelPendingTimer()
            }
        }
    }

    private fun cancelPendingTimer() {
        autoAnswerRunnable?.let { autoAnswerHandler?.removeCallbacks(it) }
        autoAnswerHandler = null
        autoAnswerRunnable = null
    }

    @SuppressLint("MissingPermission")
    private fun answerIncomingCall(context: Context, prefs: PreferencesManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.acceptRingingCall()
            }

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true
            try {
                val maxCallVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCallVol, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
            } catch (e: Exception) {}

            executeSofiRealtimeProtocol(context, prefs)

        } catch (e: Exception) {
            e.printStackTrace()
            prefs.addActivityLog("Could not auto-answer call: ${e.localizedMessage}")
        }
    }

    private fun executeSofiRealtimeProtocol(context: Context, prefs: PreferencesManager) {
        val name = currentCallerName
        val number = currentCallerNumber

        val isFamily = !name.isNullOrEmpty() && (
                name.contains("Dad", ignoreCase = true) ||
                name.contains("Mom", ignoreCase = true) ||
                name.contains("Brother", ignoreCase = true) ||
                name.contains("Sister", ignoreCase = true) ||
                name.contains("Family", ignoreCase = true)
        )

        val relationship = when {
            isFamily -> "Family ($name)"
            !name.isNullOrEmpty() -> "Contact ($name)"
            else -> "Unknown Caller"
        }

        // 1. Context-Aware Greeting
        val greeting = if (!name.isNullOrEmpty()) {
            "Hi $name, this is Sofi, Sakthi's AI assistant. Sakthi isn't able to take your call right now, but I'm here to help or take a message."
        } else {
            "Hello, thanks for calling. This is Sofi, Sakthi's AI assistant. Sakthi is currently unavailable — could I get your name?"
        }

        // 2. Question adapted to relationship
        val question = if (isFamily) "Is everything okay?" else "What's this regarding?"

        // 3. Callback Number Confirmation
        val numberConfirmation = if (!number.isNullOrEmpty()) {
            "I have your number as $number — is that correct, or would you like to leave a different callback number?"
        } else {
            "Could you please leave your callback number? Please say it slowly, digit by digit."
        }

        // 4. Emergency / Urgency Check
        val urgencyLevel = if (isFamily) "HIGH (Family Call)" else "NORMAL"
        val urgencyResponse = if (isFamily) " This sounds important — I'll notify Sakthi immediately." else ""

        // 5. Closing Warm Message
        val closeName = name ?: "there"
        val closing = "$urgencyResponse Thanks $closeName, I'll make sure Sakthi gets this as soon as possible. Take care!"

        val fullSpeech = "$greeting $question $numberConfirmation $closing"

        // 6. Structured Log Output for Sakthi
        val logOutput = """
            📋 CALL LOG FOR SAKTHI:
            Caller: ${name ?: number ?: "Unknown"}
            Relationship: $relationship
            Message: Sofi delivered message request and confirmed callback details.
            Urgency: $urgencyLevel
            Callback requested: Yes (${number ?: "Provided by caller"})
        """.trimIndent()

        prefs.addActivityLog(logOutput)

        // Pop-up Alert Banner Notification for Sakthi
        val notifHelper = NotificationHelper(context)
        notifHelper.showCallerMessagePopup(
            number ?: "Unknown Caller",
            "Sofi auto-attended call from $relationship. Urgency: $urgencyLevel."
        )

        // Speak on live call audio stream and disconnect call when finished
        speakAndEndCall(context, fullSpeech)
    }

    private fun speakAndEndCall(context: Context, speechText: String) {
        ttsEngine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureAlexaFemaleVoice(ttsEngine)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    ttsEngine?.setAudioAttributes(audioAttributes)
                }

                val utteranceId = "SOFI_REALTIME_CALL_${System.currentTimeMillis()}"

                ttsEngine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            endCall(context)
                        }, 1000)
                    }
                    override fun onError(utteranceId: String?) {
                        endCall(context)
                    }
                })

                val params = Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
                }
                ttsEngine?.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } else {
                endCall(context)
            }
        }
    }

    private fun configureAlexaFemaleVoice(tts: TextToSpeech?) {
        if (tts == null) return
        try {
            tts.language = Locale.US
            tts.setPitch(1.20f)
            tts.setSpeechRate(0.92f) // Unhurried, natural conversational pace

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val voices = tts.voices
                if (!voices.isNullOrEmpty()) {
                    val maleIdentifiers = listOf("male", "iom", "iad", "iog", "rec", "guy", "david", "mark", "george", "john", "michael")
                    val femaleIdentifiers = listOf("female", "sfg", "tpf", "iol", "wnt", "fem", "woman", "zira", "eva", "victoria", "jenny", "aria")

                    var selected = voices.find { v ->
                        val n = v.name.lowercase()
                        v.locale.language == Locale.US.language &&
                                femaleIdentifiers.any { n.contains(it) } &&
                                !maleIdentifiers.any { n.contains(it) }
                    }

                    if (selected == null) {
                        selected = voices.find { v ->
                            val n = v.name.lowercase()
                            v.locale.language == Locale.US.language &&
                                    !maleIdentifiers.any { n.contains(it) }
                        }
                    }

                    if (selected != null) {
                        tts.voice = selected
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun endCall(context: Context) {
        try {
            SofiInCallService.disconnectCall()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.endCall()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return it.getString(nameIndex)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
