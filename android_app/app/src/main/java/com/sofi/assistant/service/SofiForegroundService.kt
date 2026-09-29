package com.sofi.assistant.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sofi.assistant.ai.SofiAiEngine
import com.sofi.assistant.utils.NotificationHelper
import com.sofi.assistant.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SofiForegroundService : Service() {

    private lateinit var prefs: PreferencesManager
    private lateinit var aiEngine: SofiAiEngine
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    // Overlay speech recognizer
    private var speechRecognizer: SpeechRecognizer? = null

    // Background continuous speech recognizer
    private var bgSpeechRecognizer: SpeechRecognizer? = null
    private var isBgListeningLoopActive = false

    private var tts: TextToSpeech? = null

    companion object {
        var instance: SofiForegroundService? = null
            private set

        fun triggerOverlayAssistant(context: Context) {
            instance?.showSofiAssistantOverlay()
        }

        fun updateBackgroundListeningState(context: Context) {
            instance?.startOrStopBackgroundListening()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PreferencesManager(this)
        aiEngine = SofiAiEngine(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val notificationHelper = NotificationHelper(this)
        val notification = notificationHelper.buildServiceNotification()
        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)

        initTts()
        startOrStopBackgroundListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_TRIGGER_SOFI_OVERLAY") {
            showSofiAssistantOverlay()
        }
        startOrStopBackgroundListening()
        return START_STICKY
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(1.05f)
                tts?.setSpeechRate(0.98f)
            }
        }
    }

    // ── Continuous Background Voice Listener ─────────────────────────────────

    fun startOrStopBackgroundListening() {
        if (prefs.isAlwaysListeningEnabled) {
            startBackgroundVoiceListening()
        } else {
            stopBackgroundVoiceListening()
        }
    }

    private fun startBackgroundVoiceListening() {
        if (!prefs.isAlwaysListeningEnabled || isBgListeningLoopActive || overlayView != null) return
        isBgListeningLoopActive = true

        Handler(Looper.getMainLooper()).post {
            try {
                bgSpeechRecognizer?.destroy()
                bgSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                }

                bgSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val command = matches[0]
                            processBackgroundVoiceCommand(command)
                        } else {
                            restartBackgroundListeningDelayed(1000)
                        }
                    }

                    override fun onError(error: Int) {
                        restartBackgroundListeningDelayed(1500)
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                bgSpeechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                restartBackgroundListeningDelayed(3000)
            }
        }
    }

    private fun restartBackgroundListeningDelayed(delayMs: Long) {
        isBgListeningLoopActive = false
        Handler(Looper.getMainLooper()).postDelayed({
            if (prefs.isAlwaysListeningEnabled && overlayView == null) {
                startBackgroundVoiceListening()
            }
        }, delayMs)
    }

    private fun stopBackgroundVoiceListening() {
        isBgListeningLoopActive = false
        Handler(Looper.getMainLooper()).post {
            try {
                bgSpeechRecognizer?.destroy()
            } catch (e: Exception) {}
            bgSpeechRecognizer = null
        }
    }

    private fun processBackgroundVoiceCommand(command: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val parsedIntent = withContext(Dispatchers.IO) {
                aiEngine.parseCommandIntent(command)
            }

            var handled = false
            var statusMsg = ""

            when (parsedIntent.intentType) {
                "OPEN_APP" -> {
                    val app = parsedIntent.appName ?: "application"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.openApp(this@SofiForegroundService, app)
                    handled = res.first
                    statusMsg = res.second
                }
                "WHATSAPP_SEND" -> {
                    val target = parsedIntent.contact ?: "contact"
                    val msg = parsedIntent.messageText ?: "Hello!"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.sendWhatsAppMessage(this@SofiForegroundService, target, msg)
                    handled = res.first
                    statusMsg = res.second
                }
                "MAKE_CALL" -> {
                    val target = parsedIntent.contact ?: ""
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.makePhoneCall(this@SofiForegroundService, target)
                    handled = res.first
                    statusMsg = res.second
                }
                "SEND_SMS" -> {
                    val target = parsedIntent.contact ?: ""
                    val msg = parsedIntent.messageText ?: "Hello!"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.sendSmsMessage(this@SofiForegroundService, target, msg)
                    handled = res.first
                    statusMsg = res.second
                }
                "SYSTEM_ACTION" -> {
                    val act = parsedIntent.actionName ?: "home"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.performSystemAction(this@SofiForegroundService, act)
                    handled = res.first
                    statusMsg = res.second
                }
            }

            if (handled) {
                speak(statusMsg)
                prefs.addActivityLog("Always-On Voice Action: $statusMsg")
            }

            restartBackgroundListeningDelayed(2000)
        }
    }

    // ── Floating Overlay Assistant Interface ─────────────────────────────────

    @SuppressLint("InflateParams")
    fun showSofiAssistantOverlay() {
        if (overlayView != null) return
        stopBackgroundVoiceListening() // Temporarily pause BG loop while overlay is open

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 50
        }

        val overlayLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF00F172A.toInt())
            setPadding(32, 32, 32, 32)
        }

        val titleTv = TextView(this).apply {
            text = "⚡ Sofi AI Assistant"
            textSize = 16f
            setTextColor(0xFF818CF8.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val statusTv = TextView(this).apply {
            text = "Listening for your instruction..."
            textSize = 13f
            setTextColor(0xFF38BDF8.toInt())
            setPadding(0, 8, 0, 16)
        }

        val responseTv = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(0xFFF8FAFC.toInt())
            visibility = View.GONE
            setPadding(0, 8, 0, 16)
        }

        val btnClose = Button(this).apply {
            text = "Dismiss"
            setBackgroundColor(0x33FFFFFF.toInt())
            setTextColor(0xFF94A3B8.toInt())
            textSize = 12f
            setOnClickListener { hideOverlay() }
        }

        overlayLayout.addView(titleTv)
        overlayLayout.addView(statusTv)
        overlayLayout.addView(responseTv)
        overlayLayout.addView(btnClose)

        overlayView = overlayLayout

        try {
            windowManager?.addView(overlayView, layoutParams)
            startVoiceListening(statusTv, responseTv)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Enable 'Display over other apps' for Sofi", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVoiceListening(statusTv: TextView, responseTv: TextView) {
        Handler(Looper.getMainLooper()).post {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusTv.text = "🎤 Speak your instruction now..."
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val command = matches[0]
                        statusTv.text = "Processing: \"$command\""
                        processSystemWideInstruction(command, statusTv, responseTv)
                    }
                }

                override fun onError(error: Int) {
                    statusTv.text = "Voice timeout. Say 'Hey Sofi' to trigger again."
                    Handler(Looper.getMainLooper()).postDelayed({ hideOverlay() }, 2000)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
        }
    }

    private fun processSystemWideInstruction(command: String, statusTv: TextView, responseTv: TextView) {
        CoroutineScope(Dispatchers.Main).launch {
            val parsedIntent = withContext(Dispatchers.IO) {
                aiEngine.parseCommandIntent(command)
            }

            var handled = false
            var statusMsg = ""

            when (parsedIntent.intentType) {
                "OPEN_APP" -> {
                    val app = parsedIntent.appName ?: "application"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.openApp(this@SofiForegroundService, app)
                    handled = res.first
                    statusMsg = res.second
                }
                "WHATSAPP_SEND" -> {
                    val target = parsedIntent.contact ?: "contact"
                    val msg = parsedIntent.messageText ?: "Hello!"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.sendWhatsAppMessage(this@SofiForegroundService, target, msg)
                    handled = res.first
                    statusMsg = res.second
                }
                "MAKE_CALL" -> {
                    val target = parsedIntent.contact ?: ""
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.makePhoneCall(this@SofiForegroundService, target)
                    handled = res.first
                    statusMsg = res.second
                }
                "SEND_SMS" -> {
                    val target = parsedIntent.contact ?: ""
                    val msg = parsedIntent.messageText ?: "Hello!"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.sendSmsMessage(this@SofiForegroundService, target, msg)
                    handled = res.first
                    statusMsg = res.second
                }
                "SYSTEM_ACTION" -> {
                    val act = parsedIntent.actionName ?: "home"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.performSystemAction(this@SofiForegroundService, act)
                    handled = res.first
                    statusMsg = res.second
                }
            }

            if (handled) {
                statusTv.text = statusMsg
                speak(statusMsg)
                Handler(Looper.getMainLooper()).postDelayed({ hideOverlay() }, 1500)
            } else {
                val aiReply = withContext(Dispatchers.IO) {
                    aiEngine.generateResponse(command)
                }
                statusTv.text = "Sofi:"
                responseTv.visibility = View.VISIBLE
                responseTv.text = aiReply
                speak(aiReply)
                Handler(Looper.getMainLooper()).postDelayed({ hideOverlay() }, 5000)
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SOFI_OVERLAY_TTS")
    }

    private fun hideOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
        speechRecognizer?.destroy()
        speechRecognizer = null
        startOrStopBackgroundListening() // Resume continuous background voice loop
    }

    override fun onDestroy() {
        hideOverlay()
        stopBackgroundVoiceListening()
        tts?.shutdown()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
