package com.sofi.assistant.utils

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract

object ContactUtils {

    data class ContactInfo(val name: String, val phoneNumber: String)

    /**
     * Search for a phone number matching a contact name (case-insensitive fuzzy match).
     * If raw input is already a phone number (e.g., contains digits like "+919876543210"), returns raw input.
     */
    fun resolvePhoneNumber(context: Context, contactOrNumber: String): ContactInfo? {
        val trimmed = contactOrNumber.trim()
        if (trimmed.isEmpty()) return null

        // Check if string is already numeric/phone number format
        val digitsOnly = trimmed.replace(Regex("[^0-9+]"), "")
        if (digitsOnly.length >= 7 && (trimmed.startsWith("+") || trimmed.first().isDigit())) {
            return ContactInfo(name = trimmed, phoneNumber = digitsOnly)
        }

        val contentResolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$trimmed%")

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                val foundName = if (nameIndex >= 0) cursor.getString(nameIndex) else trimmed
                val rawNumber = if (numberIndex >= 0) cursor.getString(numberIndex) else ""
                val cleanNumber = rawNumber.replace(Regex("[^0-9+]"), "")

                if (cleanNumber.isNotEmpty()) {
                    return ContactInfo(name = foundName, phoneNumber = cleanNumber)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        // Fallback: full contact scan if LIKE query missed
        try {
            cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            if (cursor != null) {
                val lowerQuery = trimmed.lowercase()
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
                    val rawNumber = if (numberIndex >= 0) cursor.getString(numberIndex) ?: "" else ""

                    if (name.lowercase().contains(lowerQuery) || lowerQuery.contains(name.lowercase())) {
                        val cleanNumber = rawNumber.replace(Regex("[^0-9+]"), "")
                        if (cleanNumber.isNotEmpty()) {
                            return ContactInfo(name = name, phoneNumber = cleanNumber)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return null
    }
}
