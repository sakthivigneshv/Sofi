package com.sofi.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.sofi.assistant.ai.SofiAiEngine
import com.sofi.assistant.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SofiVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val prefs = PreferencesManager(context)
    private val aiEngine = SofiAiEngine(context)
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private lateinit var statusTv: TextView
    private lateinit var responseTv: TextView
    private lateinit var btnClose: Button

    override fun onCreateContentView(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF00F172A.toInt()) // Glass dark slate
            setPadding(36, 36, 36, 36)
        }

        val titleTv = TextView(context).apply {
            text = "⚡ Sofi Assistant (System Session)"
            textSize = 16f
            setTextColor(0xFF818CF8.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        statusTv = TextView(context).apply {
            text = "Initializing Sofi..."
            textSize = 13f
            setTextColor(0xFF38BDF8.toInt())
            setPadding(0, 8, 0, 16)
        }

        responseTv = TextView(context).apply {
            text = ""
            textSize = 14f
            setTextColor(0xFFF8FAFC.toInt())
            visibility = View.GONE
            setPadding(0, 8, 0, 16)
        }

        btnClose = Button(context).apply {
            text = "Dismiss"
            setBackgroundColor(0x33FFFFFF.toInt())
            setTextColor(0xFF94A3B8.toInt())
            textSize = 12f
            setOnClickListener { hide() }
        }

        layout.addView(titleTv)
        layout.addView(statusTv)
        layout.addView(responseTv)
        layout.addView(btnClose)

        initTts()

        return layout
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        startListening()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureAlexaFemaleVoice(tts)
            }
        }
    }

    private fun startListening() {
        Handler(Looper.getMainLooper()).post {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusTv.text = "🎤 Speak your instruction to Sofi..."
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val command = matches[0]
                        statusTv.text = "Processing: \"$command\""
                        processInstruction(command)
                    }
                }

                override fun onError(error: Int) {
                    statusTv.text = "Listening timed out. Tap dismiss or say 'Hey Sofi'."
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

    private fun processInstruction(command: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val parsedIntent = withContext(Dispatchers.IO) {
                aiEngine.parseCommandIntent(command)
            }

            var handled = false
            var statusMsg = ""

            when (parsedIntent.intentType) {
                "OPEN_APP" -> {
                    val app = parsedIntent.appName ?: "application"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.openApp(context, app)
                    handled = res.first
                    statusMsg = res.second
                }
                "WHATSAPP_SEND" -> {
                    val target = parsedIntent.contact ?: "contact"
                    val msg = parsedIntent.messageText ?: "Hello!"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.sendWhatsAppMessage(context, target, msg)
                    handled = res.first
                    statusMsg = res.second
                }
                "MAKE_CALL" -> {
                    val target = parsedIntent.contact ?: ""
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.makePhoneCall(context, target)
                    handled = res.first
                    statusMsg = res.second
                }
                "SEND_SMS" -> {
                    val target = parsedIntent.contact ?: ""
                    val msg = parsedIntent.messageText ?: "Hello!"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.sendSmsMessage(context, target, msg)
                    handled = res.first
                    statusMsg = res.second
                }
                "SYSTEM_ACTION" -> {
                    val act = parsedIntent.actionName ?: "home"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.performSystemAction(context, act)
                    handled = res.first
                    statusMsg = res.second
                }
            }

            if (handled) {
                statusTv.text = statusMsg
                speakAndLoop(statusMsg, closeSession = true)
            } else {
                val aiReply = withContext(Dispatchers.IO) {
                    aiEngine.generateResponse(command)
                }
                statusTv.text = "Sofi:"
                responseTv.visibility = View.VISIBLE
                responseTv.text = aiReply
                speakAndLoop(aiReply, closeSession = false)
            }
        }
    }

    private fun speakAndLoop(text: String, closeSession: Boolean) {
        val utteranceId = "SOFI_SESSION_UTT_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (closeSession) {
                    Handler(Looper.getMainLooper()).postDelayed({ hide() }, 1000)
                } else {
                    // Loop back to listening for follow-up prompt
                    Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 1500)
                }
            }
            override fun onError(utteranceId: String?) {
                if (closeSession) hide() else startListening()
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun configureAlexaFemaleVoice(tts: TextToSpeech?) {
        if (tts == null) return
        try {
            tts.language = Locale.US
            tts.setPitch(1.20f)
            tts.setSpeechRate(0.95f)

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

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.shutdown()
        super.onDestroy()
    }
}
