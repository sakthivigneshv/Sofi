package com.sofi.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "SofiPrefs"
        private const val KEY_AUTO_CALL = "auto_call_enabled"
        private const val KEY_CALL_DELAY = "call_delay_seconds"
        private const val KEY_AUTO_SMS = "auto_sms_enabled"
        private const val KEY_IMPORTANT_NOTIF = "important_notif_enabled"
        private const val KEY_ALWAYS_LISTENING = "always_listening_enabled"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_ACTIVITY_LOGS = "activity_logs"
    }

    var isAutoCallEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CALL, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CALL, value).apply()

    var callAnswerDelaySeconds: Int
        get() = prefs.getInt(KEY_CALL_DELAY, 10)
        set(value) = prefs.edit().putInt(KEY_CALL_DELAY, value).apply()

    var isAutoSmsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SMS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SMS, value).apply()

    var isImportantNotifEnabled: Boolean
        get() = prefs.getBoolean(KEY_IMPORTANT_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_IMPORTANT_NOTIF, value).apply()

    var isAlwaysListeningEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_LISTENING, true)
        set(value) = prefs.edit().putBoolean(KEY_ALWAYS_LISTENING, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var systemPrompt: String
        get() = prefs.getString(
            KEY_SYSTEM_PROMPT,
            "You are Sofi, an intelligent and polite personal AI assistant. Keep responses natural, helpful, and concise."
        ) ?: ""
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    fun getActivityLogs(): List<String> {
        val json = prefs.getString(KEY_ACTIVITY_LOGS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addActivityLog(message: String) {
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formattedLog = "[$timeStamp] $message"
        val currentLogs = getActivityLogs().toMutableList()
        currentLogs.add(0, formattedLog) // Newest first
        if (currentLogs.size > 50) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        val json = gson.toJson(currentLogs)
        prefs.edit().putString(KEY_ACTIVITY_LOGS, json).apply()
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_ACTIVITY_LOGS).apply()
    }
}
