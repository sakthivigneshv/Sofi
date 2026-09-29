package com.sofi.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sofi.assistant.ai.SofiAiEngine
import com.sofi.assistant.service.SofiForegroundService
import com.sofi.assistant.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var aiEngine: SofiAiEngine
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    // Layout views
    private lateinit var layoutPermissions: LinearLayout
    private lateinit var btnGrantPermissions: Button

    private lateinit var switchAutoCall: Switch
    private lateinit var tvCallDelay: TextView
    private lateinit var seekCallDelay: SeekBar

    private lateinit var switchAutoSms: Switch
    private lateinit var switchImportantNotif: Switch

    private lateinit var etApiKey: EditText
    private lateinit var btnSaveApiKey: Button

    private lateinit var btnTabControls: Button
    private lateinit var btnTabAssistant: Button
    private lateinit var btnTabLogs: Button

    private lateinit var viewControls: View
    private lateinit var viewAssistant: View
    private lateinit var viewLogs: View

    private lateinit var chatLogContainer: LinearLayout
    private lateinit var etMessageInput: EditText
    private lateinit var btnSendMsg: Button
    private lateinit var btnMicVoice: Button

    private lateinit var tvLogsOutput: TextView
    private lateinit var btnClearLogs: Button

    private val PERMISSIONS_REQUEST_CODE = 1001

    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)
        aiEngine = SofiAiEngine(this)

        initNativeUi()
        initServicesAndTts()
        checkPermissions()
    }

    private fun initNativeUi() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF080B11.toInt())
            setPadding(32, 48, 32, 32)
        }

        val headerTv = TextView(this).apply {
            text = "⚡ Sofi AI Mobile Assistant"
            textSize = 22f
            setTextColor(0xFF818CF8.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(headerTv)

        val subTv = TextView(this).apply {
            text = "Full Phone Control & 10s Call Attend • Nothing Phone (3)"
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 0, 0, 24)
        }
        rootLayout.addView(subTv)

        layoutPermissions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x33EF4444.toInt())
            setPadding(24, 24, 24, 24)
            visibility = View.GONE
        }
        val permTv = TextView(this).apply {
            text = "⚠️ Telephony & Phone Control permissions required."
            setTextColor(0xFFFCA5A5.toInt())
            textSize = 13f
        }
        btnGrantPermissions = Button(this).apply {
            text = "Grant Mobile Permissions"
            setBackgroundColor(0xFFEF4444.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { requestPermissions() }
        }
        layoutPermissions.addView(permTv)
        layoutPermissions.addView(btnGrantPermissions)
        rootLayout.addView(layoutPermissions)

        val tabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        btnTabControls = createTabButton("Control Center", true) { showTab(1) }
        btnTabAssistant = createTabButton("Sofi Voice & Chat", false) { showTab(2) }
        btnTabLogs = createTabButton("Logs", false) { showTab(3) }
        tabContainer.addView(btnTabControls, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabContainer.addView(btnTabAssistant, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabContainer.addView(btnTabLogs, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        rootLayout.addView(tabContainer)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }
        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        viewControls = createControlsView()
        viewAssistant = createAssistantView()
        viewLogs = createLogsView()

        contentContainer.addView(viewControls)
        contentContainer.addView(viewAssistant)
        contentContainer.addView(viewLogs)

        scrollView.addView(contentContainer)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
        showTab(1)
    }

    private fun createTabButton(title: String, active: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = title
            textSize = 12f
            setOnClickListener { onClick() }
            if (active) {
                setBackgroundColor(0xFF6366F1.toInt())
                setTextColor(0xFFFFFFFF.toInt())
            } else {
                setBackgroundColor(0x22FFFFFF.toInt())
                setTextColor(0xFF94A3B8.toInt())
            }
        }
    }

    private fun showTab(tabIndex: Int) {
        viewControls.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        viewAssistant.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        viewLogs.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE

        btnTabControls.setBackgroundColor(if (tabIndex == 1) 0xFF6366F1.toInt() else 0x22FFFFFF.toInt())
        btnTabAssistant.setBackgroundColor(if (tabIndex == 2) 0xFF6366F1.toInt() else 0x22FFFFFF.toInt())
        btnTabLogs.setBackgroundColor(if (tabIndex == 3) 0xFF6366F1.toInt() else 0x22FFFFFF.toInt())

        if (tabIndex == 3) {
            refreshLogsUi()
        }
    }

    private fun createControlsView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        val cardCall = createCardView()
        switchAutoCall = Switch(this).apply {
            text = "📞 10s Call Auto-Attend (Mr. Sakthi)"
            textSize = 16f
            setTextColor(0xFFF8FAFC.toInt())
            isChecked = prefs.isAutoCallEnabled
            setOnCheckedChangeListener { _, isChecked ->
                prefs.isAutoCallEnabled = isChecked
                prefs.addActivityLog("Auto Call Attend set to $isChecked")
            }
        }
        tvCallDelay = TextView(this).apply {
            text = "Auto-Answer Delay: ${prefs.callAnswerDelaySeconds} Seconds"
            setTextColor(0xFF38BDF8.toInt())
            textSize = 13f
            setPadding(0, 12, 0, 8)
        }
        seekCallDelay = SeekBar(this).apply {
            max = 30
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                min = 3
            }
            progress = prefs.callAnswerDelaySeconds
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val delay = progress.coerceAtLeast(3)
                    tvCallDelay.text = "Auto-Answer Delay: $delay Seconds"
                    if (fromUser) {
                        prefs.callAnswerDelaySeconds = delay
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    prefs.addActivityLog("Call answer delay updated to ${prefs.callAnswerDelaySeconds}s")
                }
            })
        }
        cardCall.addView(switchAutoCall)
        cardCall.addView(tvCallDelay)
        cardCall.addView(seekCallDelay)
        container.addView(cardCall)

        val cardSms = createCardView()
        switchAutoSms = Switch(this).apply {
            text = "💬 Auto-Reply Messages & SMS"
            textSize = 16f
            setTextColor(0xFFF8FAFC.toInt())
            isChecked = prefs.isAutoSmsEnabled
            setOnCheckedChangeListener { _, isChecked ->
                prefs.isAutoSmsEnabled = isChecked
                prefs.addActivityLog("SMS Auto-Reply set to $isChecked")
            }
        }
        switchImportantNotif = Switch(this).apply {
            text = "🚨 Pop-up Alert Important Messages"
            textSize = 16f
            setTextColor(0xFFF8FAFC.toInt())
            isChecked = prefs.isImportantNotifEnabled
            setPadding(0, 16, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.isImportantNotifEnabled = isChecked
                prefs.addActivityLog("Important Message Alerts set to $isChecked")
            }
        }
        cardSms.addView(switchAutoSms)
        cardSms.addView(switchImportantNotif)
        container.addView(cardSms)

        // Card: Always-On Background Voice Engine
        val cardVoice = createCardView()
        val switchAlwaysListening = Switch(this).apply {
            text = "🎙️ Always-On Background Voice Listening"
            textSize = 16f
            setTextColor(0xFFF8FAFC.toInt())
            isChecked = prefs.isAlwaysListeningEnabled
            setOnCheckedChangeListener { _, isChecked ->
                prefs.isAlwaysListeningEnabled = isChecked
                com.sofi.assistant.service.SofiForegroundService.updateBackgroundListeningState(this@MainActivity)
                val status = if (isChecked) "ACTIVE (Waiting for commands in background)" else "OFF"
                Toast.makeText(this@MainActivity, "Always-On Voice Listening $status", Toast.LENGTH_SHORT).show()
                prefs.addActivityLog("Always-On Voice Listening set to $isChecked")
            }
        }
        val tvVoiceSub = TextView(this).apply {
            text = "Keeps Sofi continuously active in the background, listening and waiting for your voice commands."
            textSize = 12f
            setTextColor(0xFF38BDF8.toInt())
            setPadding(0, 8, 0, 0)
        }
        cardVoice.addView(switchAlwaysListening)
        cardVoice.addView(tvVoiceSub)
        container.addView(cardVoice)

        // Card 2.5: Phone Lock & System-Wide Assistant Settings
        val cardControl = createCardView()
        val ctrlTitle = TextView(this).apply {
            text = "🌟 System-Wide Digital Assistant (Siri / Google Style)"
            textSize = 15f
            setTextColor(0xFFF8FAFC.toInt())
            setPadding(0, 0, 0, 8)
        }
        val ctrlSub = TextView(this).apply {
            text = "Enable Overlay and Set Sofi as Default Device Assistant to trigger over any app!"
            textSize = 12f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(0, 0, 0, 12)
        }
        val btnOverlayPerm = Button(this).apply {
            text = "1. Enable 'Display Over Other Apps' Overlay"
            setBackgroundColor(0xFF3B82F6.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }
        val btnDefaultAssistant = Button(this).apply {
            text = "2. Set Sofi as Default Digital Assistant"
            setBackgroundColor(0xFF6366F1.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Go to Settings -> Apps -> Default Apps -> Digital Assistant -> Select Sofi", Toast.LENGTH_LONG).show()
                }
            }
        }
        val btnEnableAccess = Button(this).apply {
            text = "3. Enable Accessibility (Phone Lock & Control)"
            setBackgroundColor(0xFF8B5CF6.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Open Settings -> Accessibility -> Enable Sofi", Toast.LENGTH_LONG).show()
                }
            }
        }
        cardControl.addView(ctrlTitle)
        cardControl.addView(ctrlSub)
        cardControl.addView(btnOverlayPerm)
        cardControl.addView(btnDefaultAssistant)
        cardControl.addView(btnEnableAccess)
        container.addView(cardControl)

        val cardApi = createCardView()
        val apiTitle = TextView(this).apply {
            text = "🔑 Google Gemini API Configuration"
            textSize = 15f
            setTextColor(0xFFF8FAFC.toInt())
            setPadding(0, 0, 0, 8)
        }
        etApiKey = EditText(this).apply {
            hint = "Enter Gemini API Key (Optional)"
            setText(prefs.geminiApiKey)
            setTextColor(0xFFF8FAFC.toInt())
            setHintTextColor(0xFF64748B.toInt())
            setBackgroundColor(0x22FFFFFF.toInt())
            setPadding(20, 20, 20, 20)
        }
        btnSaveApiKey = Button(this).apply {
            text = "Save API Key"
            setBackgroundColor(0xFF10B981.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                prefs.geminiApiKey = etApiKey.text.toString()
                Toast.makeText(this@MainActivity, "API Key Saved!", Toast.LENGTH_SHORT).show()
                prefs.addActivityLog("Gemini API Key updated")
            }
        }
        cardApi.addView(apiTitle)
        cardApi.addView(etApiKey)
        cardApi.addView(btnSaveApiKey)
        container.addView(cardApi)

        return container
    }

    private fun createAssistantView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        chatLogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        etMessageInput = EditText(this).apply {
            hint = "Say or type instructions (e.g. 'open WhatsApp')..."
            setTextColor(0xFFF8FAFC.toInt())
            setHintTextColor(0xFF64748B.toInt())
            setBackgroundColor(0x22FFFFFF.toInt())
            setPadding(20, 20, 20, 20)
        }
        btnSendMsg = Button(this).apply {
            text = "Send"
            setBackgroundColor(0xFF6366F1.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val text = etMessageInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessageToSofi(text)
                    etMessageInput.setText("")
                }
            }
        }
        btnMicVoice = Button(this).apply {
            text = "🎤"
            setBackgroundColor(0xFF3B82F6.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { startVoiceRecognition() }
        }

        inputRow.addView(etMessageInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(btnMicVoice)
        inputRow.addView(btnSendMsg)

        val scrollChat = ScrollView(this).apply {
            setPadding(0, 0, 0, 16)
        }
        scrollChat.addView(chatLogContainer)

        container.addView(scrollChat, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 800))
        container.addView(inputRow)

        addChatBubble("Sofi", "Hello Mr. Sakthi! Give me any instruction (e.g., 'open WhatsApp', 'open Camera', or ask any query) and I will execute it on your phone.")

        return container
    }

    private fun createLogsView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "📋 Activity, Calls & Voicemail Log"
            textSize = 16f
            setTextColor(0xFFF8FAFC.toInt())
        }
        btnClearLogs = Button(this).apply {
            text = "Clear"
            setBackgroundColor(0x33EF4444.toInt())
            setTextColor(0xFFFCA5A5.toInt())
            setOnClickListener {
                prefs.clearLogs()
                refreshLogsUi()
            }
        }
        headerRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(btnClearLogs)

        tvLogsOutput = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF34D399.toInt())
            setTypeface(android.graphics.Typeface.MONOSPACE)
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0x22000000.toInt())
        }

        container.addView(headerRow)
        container.addView(tvLogsOutput)
        refreshLogsUi()

        return container
    }

    private fun createCardView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x1AFFFFFF.toInt())
            setPadding(24, 24, 24, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
            layoutParams = params
        }
    }

    private fun sendMessageToSofi(text: String) {
        addChatBubble("Mr. Sakthi", text)

        CoroutineScope(Dispatchers.Main).launch {
            val parsedIntent = withContext(Dispatchers.IO) {
                aiEngine.parseCommandIntent(text)
            }

            var handled = false
            var statusMsg = ""

            when (parsedIntent.intentType) {
                "OPEN_APP" -> {
                    val app = parsedIntent.appName ?: "application"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.openApp(this@MainActivity, app)
                    handled = res.first
                    statusMsg = res.second
                }
                "WHATSAPP_SEND" -> {
                    val target = parsedIntent.contact ?: "contact"
                    val msg = parsedIntent.messageText ?: "Hello!"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.sendWhatsAppMessage(this@MainActivity, target, msg)
                    handled = res.first
                    statusMsg = res.second
                }
                "MAKE_CALL" -> {
                    val target = parsedIntent.contact ?: ""
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.makePhoneCall(this@MainActivity, target)
                    handled = res.first
                    statusMsg = res.second
                }
                "SEND_SMS" -> {
                    val target = parsedIntent.contact ?: ""
                    val msg = parsedIntent.messageText ?: "Hello!"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.sendSmsMessage(this@MainActivity, target, msg)
                    handled = res.first
                    statusMsg = res.second
                }
                "SYSTEM_ACTION" -> {
                    val act = parsedIntent.actionName ?: "home"
                    val res = com.sofi.assistant.utils.PhoneAutomationEngine.performSystemAction(this@MainActivity, act)
                    handled = res.first
                    statusMsg = res.second
                }
            }

            if (handled) {
                addChatBubble("Sofi", statusMsg)
                speakTts(statusMsg)
                prefs.addActivityLog("Phone Action: $statusMsg")
            } else {
                val fallbackHandled = handlePhoneControlCommand(text)
                if (!fallbackHandled) {
                    val response = withContext(Dispatchers.IO) {
                        aiEngine.generateResponse(text)
                    }
                    addChatBubble("Sofi", response)
                    speakTts(response)
                }
            }
        }
    }

    private fun handlePhoneControlCommand(commandText: String): Boolean {
        val lower = commandText.lowercase()

        // 0. Screen Locking Command: e.g. "sofi lock my phone", "lock phone", "turn off screen"
        if (lower.contains("lock my phone") || lower.contains("lock phone") || lower.contains("lock screen") || lower.contains("off the screen") || lower.contains("off screen")) {
            val locked = com.sofi.assistant.service.SofiAccessibilityService.lockScreen()
            if (!locked) {
                val dpm = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val adminComponent = android.content.ComponentName(this, com.sofi.assistant.receiver.SofiDeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.lockNow()
                } else {
                    Toast.makeText(this, "Enable Sofi in Accessibility Settings to allow automatic screen locking.", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (e: Exception) {}
                }
            }
            val reply = "Locking your phone and turning off screen, Mr. Sakthi."
            addChatBubble("Sofi", reply)
            speakTts(reply)
            prefs.addActivityLog("Executed phone command: Locked device screen")
            return true
        }

        // Screenshot command: "take screenshot"
        if (lower.contains("take screenshot") || lower.contains("screenshot")) {
            com.sofi.assistant.service.SofiAccessibilityService.takeScreenshot()
            val reply = "Taking screenshot for you, Mr. Sakthi."
            addChatBubble("Sofi", reply)
            speakTts(reply)
            return true
        }

        // Go Home command: "go home"
        if (lower.contains("go home") || lower.contains("home screen")) {
            com.sofi.assistant.service.SofiAccessibilityService.goHome()
            val reply = "Going to home screen."
            addChatBubble("Sofi", reply)
            speakTts(reply)
            return true
        }

        // 1. App Launching Command: e.g., "open whatsapp", "open youtube", "open camera", "open settings"
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val appName = lower.substringAfter(" ").trim()
            val launched = openInstalledApp(appName)
            if (launched) {
                val reply = "Opening $appName for you, Mr. Sakthi."
                addChatBubble("Sofi", reply)
                speakTts(reply)
                prefs.addActivityLog("Executed phone command: Opened $appName")
                return true
            }
        }

        // 2. Open SMS/Messaging Command
        if (lower.contains("send message") || lower.contains("send sms")) {
            try {
                val smsIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(smsIntent)
                val reply = "Opening Messages app for you."
                addChatBubble("Sofi", reply)
                speakTts(reply)
                return true
            } catch (e: Exception) {
                // Fallback
            }
        }

        return false
    }

    private fun openInstalledApp(appName: String): Boolean {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(appName) || appName.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    return true
                }
            }
        }

        // System app defaults
        val defaultPackage = when (appName) {
            "whatsapp" -> "com.whatsapp"
            "youtube" -> "com.google.android.youtube"
            "chrome" -> "com.android.chrome"
            "settings" -> "com.android.settings"
            "camera" -> "com.android.camera"
            else -> null
        }

        if (defaultPackage != null) {
            val intent = pm.getLaunchIntentForPackage(defaultPackage)
            if (intent != null) {
                startActivity(intent)
                return true
            }
        }

        return false
    }

    private fun addChatBubble(sender: String, message: String) {
        val bubble = TextView(this).apply {
            text = "$sender: $message"
            textSize = 14f
            setPadding(20, 16, 20, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            layoutParams = params
            if (sender.contains("Sakthi") || sender == "You") {
                setBackgroundColor(0xFF312E81.toInt())
                setTextColor(0xFFE0E7FF.toInt())
            } else {
                setBackgroundColor(0xFF1E293B.toInt())
                setTextColor(0xFFF8FAFC.toInt())
            }
        }
        chatLogContainer.addView(bubble)
    }

    private fun refreshLogsUi() {
        val logs = prefs.getActivityLogs()
        tvLogsOutput.text = if (logs.isEmpty()) "No activity logged yet." else logs.joinToString("\n")
    }

    private fun initServicesAndTts() {
        try {
            val serviceIntent = Intent(this, SofiForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureAlexaFemaleVoice(tts)
            }
        }
    }

    private fun configureAlexaFemaleVoice(tts: TextToSpeech?) {
        if (tts == null) return
        try {
            tts.language = Locale.US
            tts.setPitch(1.20f) // Distinct clear female pitch elevation
            tts.setSpeechRate(0.95f) // Smooth moderate tempo

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

    private fun speakTts(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SOFI_TTS")
    }

    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "Listening for instruction...", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokeText = matches[0]
                    sendMessageToSofi(spokeText)
                }
            }
            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Voice error: $error", Toast.LENGTH_SHORT).show()
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

    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            layoutPermissions.visibility = View.VISIBLE
        } else {
            layoutPermissions.visibility = View.GONE
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            checkPermissions()
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
