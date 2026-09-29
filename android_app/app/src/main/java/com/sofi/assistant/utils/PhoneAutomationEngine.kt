package com.sofi.assistant.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.sofi.assistant.service.SofiAccessibilityService
import java.net.URLEncoder

object PhoneAutomationEngine {

    private var isTorchOn = false

    /**
     * Launch any installed Android application by name or package match.
     */
    fun openApp(context: Context, rawAppName: String): Pair<Boolean, String> {
        val appName = rawAppName.lowercase().trim()
        val pm = context.packageManager

        // Known direct package mappings
        val knownPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "whatsapp business" to "com.whatsapp.w4b",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "instagram" to "com.instagram.android",
            "spotify" to "com.spotify.music",
            "camera" to "com.android.camera",
            "settings" to "com.android.settings",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "telegram" to "org.telegram.messenger",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "phone" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar"
        )

        val directPkg = knownPackages[appName]
        if (directPkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(directPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return Pair(true, "Opening $rawAppName")
            }
        }

        // Dynamic package scan
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == appName || label.contains(appName) || appName.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return Pair(true, "Opening ${pm.getApplicationLabel(app)}")
                }
            }
        }

        return Pair(false, "Application '$rawAppName' was not found on this phone.")
    }

    /**
     * Send WhatsApp message to a contact or phone number.
     */
    fun sendWhatsAppMessage(context: Context, contactOrNumber: String, messageText: String): Pair<Boolean, String> {
        val contactInfo = ContactUtils.resolvePhoneNumber(context, contactOrNumber)
        val targetName = contactInfo?.name ?: contactOrNumber
        var phoneDigits = contactInfo?.phoneNumber ?: contactOrNumber.replace(Regex("[^0-9]"), "")

        if (phoneDigits.isEmpty()) {
            return Pair(false, "Could not find phone number for '$contactOrNumber' in contacts.")
        }

        // Clean phone number format for WhatsApp API (no leading zero, add country code if missing)
        if (!phoneDigits.startsWith("+") && phoneDigits.length == 10) {
            phoneDigits = "91$phoneDigits" // Default India prefix if 10 digits
        } else {
            phoneDigits = phoneDigits.replace("+", "")
        }

        try {
            val encodedMsg = URLEncoder.encode(messageText, "UTF-8")
            val url = "https://api.whatsapp.com/send?phone=$phoneDigits&text=$encodedMsg"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            // Trigger accessibility auto-send button click
            if (SofiAccessibilityService.isServiceActive()) {
                SofiAccessibilityService.autoClickWhatsAppSend(1400)
            }

            return Pair(true, "Opening WhatsApp to send message to $targetName")
        } catch (e: Exception) {
            // Fallback general intent
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return Pair(true, "Opened WhatsApp for $targetName")
            } catch (ex: Exception) {
                return Pair(false, "WhatsApp is not installed on this device.")
            }
        }
    }

    /**
     * Start a direct Phone call to a contact or phone number.
     */
    fun makePhoneCall(context: Context, contactOrNumber: String): Pair<Boolean, String> {
        val contactInfo = ContactUtils.resolvePhoneNumber(context, contactOrNumber)
        val targetName = contactInfo?.name ?: contactOrNumber
        val phoneNumber = contactInfo?.phoneNumber ?: contactOrNumber

        if (phoneNumber.isEmpty()) {
            return Pair(false, "Could not find contact details for '$contactOrNumber'")
        }

        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        return if (hasCallPerm) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                Pair(true, "Calling $targetName ($phoneNumber)...")
            } catch (e: Exception) {
                Pair(false, "Failed to start phone call: ${e.message}")
            }
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            Pair(true, "Opening Phone Dialer for $targetName ($phoneNumber)")
        }
    }

    /**
     * Send SMS to a contact or phone number.
     */
    fun sendSmsMessage(context: Context, contactOrNumber: String, messageText: String): Pair<Boolean, String> {
        val contactInfo = ContactUtils.resolvePhoneNumber(context, contactOrNumber)
        val targetName = contactInfo?.name ?: contactOrNumber
        val phoneNumber = contactInfo?.phoneNumber ?: contactOrNumber

        if (phoneNumber.isEmpty()) {
            return Pair(false, "Could not find phone number for '$contactOrNumber'")
        }

        val hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        if (hasSmsPerm) {
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(phoneNumber, null, messageText, null, null)
                return Pair(true, "SMS sent to $targetName: \"$messageText\"")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback: Open SMS app prefilled
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$phoneNumber")).apply {
                putExtra("sms_body", messageText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return Pair(true, "Opened SMS composer for $targetName")
        } catch (e: Exception) {
            return Pair(false, "Could not send SMS: ${e.message}")
        }
    }

    /**
     * Toggle device torch/flashlight.
     */
    fun toggleFlashlight(context: Context, enable: Boolean? = null): Pair<Boolean, String> {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            val newState = enable ?: !isTorchOn
            cameraManager.setTorchMode(cameraId, newState)
            isTorchOn = newState
            val status = if (newState) "turned ON" else "turned OFF"
            return Pair(true, "Flashlight $status")
        } catch (e: Exception) {
            return Pair(false, "Flashlight toggle failed: ${e.message}")
        }
    }

    /**
     * Perform global phone hardware and navigation actions.
     */
    fun performSystemAction(context: Context, action: String): Pair<Boolean, String> {
        val lower = action.lowercase().trim()
        return when {
            lower.contains("lock") -> {
                val ok = SofiAccessibilityService.lockScreen()
                if (ok) Pair(true, "Screen locked") else Pair(false, "Accessibility Service required to lock phone screen")
            }
            lower.contains("screenshot") -> {
                val ok = SofiAccessibilityService.takeScreenshot()
                if (ok) Pair(true, "Screenshot taken") else Pair(false, "Accessibility Service required for screenshots")
            }
            lower.contains("home") -> {
                val ok = SofiAccessibilityService.goHome()
                if (ok) Pair(true, "Returned to home screen") else Pair(false, "Accessibility Service required for home action")
            }
            lower.contains("back") -> {
                val ok = SofiAccessibilityService.goBack()
                if (ok) Pair(true, "Back action performed") else Pair(false, "Accessibility Service required")
            }
            lower.contains("recent") -> {
                val ok = SofiAccessibilityService.openRecents()
                if (ok) Pair(true, "Opened recent apps") else Pair(false, "Accessibility Service required")
            }
            lower.contains("notification") -> {
                val ok = SofiAccessibilityService.openNotifications()
                if (ok) Pair(true, "Opened notification panel") else Pair(false, "Accessibility Service required")
            }
            lower.contains("quick_setting") || lower.contains("control_center") -> {
                val ok = SofiAccessibilityService.openQuickSettings()
                if (ok) Pair(true, "Opened Quick Settings") else Pair(false, "Accessibility Service required")
            }
            lower.contains("torch") || lower.contains("flashlight") -> {
                val turnOn = lower.contains("on") || !isTorchOn
                toggleFlashlight(context, turnOn)
            }
            else -> Pair(false, "Unknown system action: $action")
        }
    }
}
