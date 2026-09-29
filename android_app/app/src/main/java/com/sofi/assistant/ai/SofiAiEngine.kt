package com.sofi.assistant.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sofi.assistant.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ParsedIntent(
    val intentType: String, // "OPEN_APP", "WHATSAPP_SEND", "MAKE_CALL", "SEND_SMS", "SYSTEM_ACTION", "CHAT"
    val appName: String? = null,
    val contact: String? = null,
    val messageText: String? = null,
    val actionName: String? = null
)

class SofiAiEngine(private val context: Context) {

    private val prefs = PreferencesManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val DEFAULT_API_KEY = ""

    private fun getApiKey(): String {
        val userKey = prefs.geminiApiKey
        return if (userKey.isNotBlank()) userKey else DEFAULT_API_KEY
    }

    suspend fun generateResponse(userMessage: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val systemPrompt = prefs.systemPrompt

        if (apiKey.isBlank()) {
            return@withContext getFallbackReply(userMessage)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val rootObj = JsonObject()
        val contentsArray = JsonArray()
        val userContentObj = JsonObject()
        userContentObj.addProperty("role", "user")

        val partsArray = JsonArray()
        val sysPart = JsonObject()
        sysPart.addProperty("text", "System Instruction: $systemPrompt\nUser Input: $userMessage")
        partsArray.add(sysPart)

        userContentObj.add("parts", partsArray)
        contentsArray.add(userContentObj)
        rootObj.add("contents", contentsArray)

        var resultText: String? = null

        try {
            val requestBody = rootObj.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBodyStr = response.body?.string() ?: ""
                val resObj = gson.fromJson(responseBodyStr, JsonObject::class.java)
                val candidates = resObj.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val firstCandidate = candidates.get(0).asJsonObject
                    val content = firstCandidate.getAsJsonObject("content")
                    val parts = content.getAsJsonArray("parts")
                    if (parts != null && parts.size() > 0) {
                        resultText = parts.get(0).asJsonObject.get("text").asString.trim()
                    }
                }
            }
            response.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext resultText ?: getFallbackReply(userMessage)
    }

    /**
     * Parse natural user command into structured ParsedIntent.
     * Uses ultra-fast local rule engine first, fallback to Gemini AI for complex phrasing.
     */
    suspend fun parseCommandIntent(userMessage: String): ParsedIntent = withContext(Dispatchers.IO) {
        val lower = userMessage.lowercase().trim()

        // 1. WhatsApp send intent: "whatsapp send hi to mom", "open whatsapp and send hello to Rahul", "send message to Rahul on WhatsApp saying hello"
        if (lower.contains("whatsapp")) {
            val contact = extractContactName(lower)
            val msg = extractMessageText(userMessage)
            return@withContext ParsedIntent(
                intentType = "WHATSAPP_SEND",
                appName = "whatsapp",
                contact = contact,
                messageText = msg
            )
        }

        // 2. Phone Call intent: "call mom", "make a call to rahul", "dial 9876543210"
        if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.contains("make a call to ") || lower.contains("phone call to ")) {
            val contact = lower.replace(Regex("^(call|dial|make a call to|phone call to)\\s+"), "").trim()
            return@withContext ParsedIntent(
                intentType = "MAKE_CALL",
                contact = contact
            )
        }

        // 3. SMS intent: "send sms to rahul saying hello", "send text to mom"
        if (lower.contains("send sms") || lower.contains("send text") || (lower.contains("send message") && !lower.contains("whatsapp"))) {
            val contact = extractContactName(lower)
            val msg = extractMessageText(userMessage)
            return@withContext ParsedIntent(
                intentType = "SEND_SMS",
                contact = contact,
                messageText = msg
            )
        }

        // 4. Global System Actions
        val systemKeywords = mapOf(
            "lock phone" to "lock", "lock screen" to "lock", "turn off screen" to "lock",
            "take screenshot" to "screenshot", "screenshot" to "screenshot",
            "go home" to "home", "home screen" to "home",
            "go back" to "back",
            "recent apps" to "recents", "app switcher" to "recents",
            "open notifications" to "notifications", "notifications" to "notifications",
            "quick settings" to "quick_settings", "control center" to "quick_settings",
            "turn on flashlight" to "torch_on", "turn off flashlight" to "torch_off", "flashlight" to "torch"
        )
        for ((key, act) in systemKeywords) {
            if (lower.contains(key)) {
                return@withContext ParsedIntent(
                    intentType = "SYSTEM_ACTION",
                    actionName = act
                )
            }
        }

        // 5. Open App intent: "open whatsapp", "launch youtube", "open camera", "open instagram"
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) {
            val targetApp = lower.replace(Regex("^(open|launch|start)\\s+"), "").trim()
            return@withContext ParsedIntent(
                intentType = "OPEN_APP",
                appName = targetApp
            )
        }

        // 6. Gemini AI Deep Intent Parser for complex natural queries
        try {
            val prompt = """
                Analyze this user phone command: "$userMessage"
                Respond strictly in raw JSON without markdown formatting matching this schema:
                {
                  "intentType": "OPEN_APP" | "WHATSAPP_SEND" | "MAKE_CALL" | "SEND_SMS" | "SYSTEM_ACTION" | "CHAT",
                  "appName": "app name if intent is OPEN_APP or WHATSAPP_SEND",
                  "contact": "contact name or phone number",
                  "messageText": "message text if applicable",
                  "actionName": "lock" | "screenshot" | "home" | "back" | "recents" | "notifications" | "torch"
                }
            """.trimIndent()
            val aiResult = generateResponse(prompt)
            val jsonStr = aiResult.substringAfter("{").substringBeforeLast("}")
            if (jsonStr.isNotBlank()) {
                val parsed = gson.fromJson("{$jsonStr}", JsonObject::class.java)
                return@withContext ParsedIntent(
                    intentType = parsed.get("intentType")?.asString ?: "CHAT",
                    appName = parsed.get("appName")?.asString,
                    contact = parsed.get("contact")?.asString,
                    messageText = parsed.get("messageText")?.asString,
                    actionName = parsed.get("actionName")?.asString
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext ParsedIntent(intentType = "CHAT")
    }

    private fun extractContactName(text: String): String {
        val patterns = listOf(
            Regex("(?:to|contact)\\s+([a-zA-Z0-9_+\\s]+?)(?:\\s+saying|\\s+that|\\s+message|\\s+on|\\$|$)"),
            Regex("whatsapp\\s+([a-zA-Z0-9_+\\s]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (name.isNotEmpty() && name != "whatsapp") return name
            }
        }
        return "Contact"
    }

    private fun extractMessageText(text: String): String {
        val patterns = listOf(
            Regex("(?:saying|that|text|msg|message)\\s+(.+)"),
            Regex("send\\s+\"([^\"]+)\""),
            Regex("send\\s+(.+)\\s+to")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val msg = match.groupValues[1].trim()
                if (msg.isNotEmpty()) return msg
            }
        }
        return "Hello!"
    }

    suspend fun generateSmsAutoReply(sender: String, messageText: String): String = withContext(Dispatchers.IO) {
        val prompt = "Incoming SMS from $sender: \"$messageText\". " +
                "You are Sofi, an automated mobile assistant. Generate a polite, short SMS response (under 140 chars) acknowledging the message."
        return@withContext generateResponse(prompt)
    }

    suspend fun checkIsImportantMessage(sender: String, messageText: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val lowerMsg = messageText.lowercase()

        val urgentKeywords = listOf("urgent", "emergency", "otp", "code", "verification", "hospital", "doctor", "accident", "help", "meet me", "call me now")
        for (keyword in urgentKeywords) {
            if (lowerMsg.contains(keyword)) {
                return@withContext Pair(true, "Contains priority term: '$keyword'")
            }
        }

        val evalPrompt = "Evaluate if this incoming message requires urgent user attention (True/False) and why. Message from $sender: \"$messageText\". Respond strictly in JSON format: {\"important\": true/false, \"reason\": \"short reason\"}"
        val aiResult = generateResponse(evalPrompt)

        try {
            if (aiResult.contains("true", ignoreCase = true)) {
                return@withContext Pair(true, "AI classified as high priority")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext Pair(false, "Normal message")
    }

    private fun getFallbackReply(messageText: String): String {
        val lower = messageText.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") -> "Hello! I am Sofi AI assistant. How can I help you?"
            lower.contains("who are you") -> "I am Sofi, your native Android AI assistant."
            else -> "Thank you for your message! Sofi has recorded it."
        }
    }
}
