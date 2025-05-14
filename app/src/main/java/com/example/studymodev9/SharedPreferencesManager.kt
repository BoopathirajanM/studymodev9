package com.example.studymodev9

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.util.*

class SharedPreferencesManager private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "StudyModePrefs"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
        private const val KEY_EMERGENCY_APPS = "emergency_apps"
        private const val KEY_STUDY_SESSIONS = "study_sessions"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        
        @Volatile
        private var instance: SharedPreferencesManager? = null

        fun getInstance(context: Context): SharedPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: SharedPreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }

    data class Contact(val name: String, val number: String)

    fun saveEmergencyContact(name: String, number: String) {
        val contacts = getEmergencyContacts().toMutableList()
        if (contacts.size < 5 && !contacts.any { it.number == number }) {
            contacts.add(Contact(name, number))
            val serializedContacts = contacts.joinToString("|") { "${it.name},${it.number}" }
            sharedPreferences.edit().putString(KEY_EMERGENCY_CONTACTS, serializedContacts).apply()
        }
    }

    fun getEmergencyContacts(): List<Contact> {
        try {
            // Try to get the new format (serialized string)
            val serializedContacts = sharedPreferences.getString(KEY_EMERGENCY_CONTACTS, null)
            
            if (serializedContacts != null && serializedContacts.isNotEmpty()) {
                // New format is available, use it
                return serializedContacts.split("|").mapNotNull {
                    try {
                        val parts = it.split(",")
                        if (parts.size >= 2) {
                            Contact(parts[0], parts[1])
                        } else {
                            null // Skip malformed entries
                        }
                    } catch (e: Exception) {
                        Log.e("SharedPrefManager", "Error parsing contact: $it", e)
                        null // Skip entries that cause exceptions
                    }
                }
            } else {
                // Try to get the old format (string set)
                val oldContacts = sharedPreferences.getStringSet(KEY_EMERGENCY_CONTACTS, emptySet())
                
                if (oldContacts != null && oldContacts.isNotEmpty()) {
                    // Convert old format to new format and save it
                    val contacts = oldContacts.map { Contact(it, it) }
                    val serializedNewContacts = contacts.joinToString("|") { "${it.name},${it.number}" }
                    sharedPreferences.edit().putString(KEY_EMERGENCY_CONTACTS, serializedNewContacts).apply()
                    return contacts
                }
            }
            
            // No contacts found in either format
            return emptyList()
        } catch (e: Exception) {
            Log.e("SharedPrefManager", "Error getting emergency contacts", e)
            e.printStackTrace()
            // Return empty list in case of any error
            return emptyList()
        }
    }

    fun saveEmergencyApp(appName: String, packageName: String) {
        try {
            // Get current apps directly from preferences to avoid recursion
            val currentApps = getEmergencyAppsRaw().toMutableMap()
            currentApps[appName] = packageName
            val json = gson.toJson(currentApps)
            sharedPreferences.edit().putString(KEY_EMERGENCY_APPS, json).apply()
        } catch (e: Exception) {
            Log.e("SharedPrefManager", "Error saving emergency app", e)
        }
    }

    private fun getEmergencyAppsRaw(): Map<String, String> {
        val storedValue = sharedPreferences.getString(KEY_EMERGENCY_APPS, null)
        if (storedValue == null || storedValue == "{}") {
            return emptyMap()
        }

        // Try parsing as JSON format
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(storedValue, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getEmergencyApps(): Map<String, String> {
        try {
            val storedValue = sharedPreferences.getString(KEY_EMERGENCY_APPS, null)
            if (storedValue == null || storedValue == "{}") {
                return emptyMap()
            }

            // Try parsing as new JSON format
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                return gson.fromJson(storedValue, type) ?: emptyMap()
            } catch (e: JsonSyntaxException) {
                // If JSON parsing fails, try parsing old format and migrate
                try {
                    // Old format was using | as separator and , for key-value pairs
                    val migratedData = storedValue.split("|")
                        .filter { it.isNotEmpty() }
                        .map { 
                            val parts = it.split(",")
                            if (parts.size >= 2) {
                                parts[0].trim() to parts[1].trim()
                            } else {
                                null
                            }
                        }
                        .filterNotNull()
                        .toMap()

                    // Migrate to new format if we have data
                    if (migratedData.isNotEmpty()) {
                        val json = gson.toJson(migratedData)
                        sharedPreferences.edit().putString(KEY_EMERGENCY_APPS, json).apply()
                    }

                    return migratedData
                } catch (e: Exception) {
                    Log.e("SharedPrefManager", "Error parsing old format", e)
                    return emptyMap()
                }
            }
        } catch (e: Exception) {
            Log.e("SharedPrefManager", "Error getting emergency apps", e)
            return emptyMap()
        }
    }

    fun removeEmergencyApp(appName: String) {
        val apps = getEmergencyApps().toMutableMap()
        apps.remove(appName)
        val json = gson.toJson(apps)
        sharedPreferences.edit().putString(KEY_EMERGENCY_APPS, json).apply()
    }

    fun saveBlockedApp(appName: String, packageName: String) {
        val apps = getBlockedApps().toMutableMap()
        apps[appName] = packageName
        val json = gson.toJson(apps)
        sharedPreferences.edit().putString(KEY_BLOCKED_APPS, json).apply()
    }

    fun getBlockedApps(): Map<String, String> {
        val json = sharedPreferences.getString(KEY_BLOCKED_APPS, "{}")
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    fun removeBlockedApp(appName: String) {
        val apps = getBlockedApps().toMutableMap()
        apps.remove(appName)
        val json = gson.toJson(apps)
        sharedPreferences.edit().putString(KEY_BLOCKED_APPS, json).apply()
    }

    fun saveWeeklyStudyHours(hoursList: FloatArray) {
        // Save the updated weekly study hours back to SharedPreferences
        sharedPreferences.edit().putString(KEY_STUDY_SESSIONS, gson.toJson(hoursList)).apply()
    }

    fun addStudyHoursToDay(dayIndex: Int, hoursToAdd: Float) {
        val hoursList = getWeeklyStudyHours().toMutableList()  // Get current weekly hours list
        if (dayIndex in 0..6) {  // Ensure the dayIndex is valid (0 = Mon, 6 = Sun)
            hoursList[dayIndex] += hoursToAdd  // Add the study hours to the selected day
        }
        saveWeeklyStudyHours(hoursList.toFloatArray())  // Save the updated list back to SharedPreferences
    }

    fun saveStudySession(session: StudySession) {
        val sessions = getStudySessions().toMutableList()
        sessions.add(session)
        val json = gson.toJson(sessions)
        sharedPreferences.edit().putString(KEY_STUDY_SESSIONS, json).apply()
        Log.d("SharedPrefs", "Session added: $session")  // Log session being saved
    }

    fun getStudySessions(): List<StudySession> {
        val json = sharedPreferences.getString(KEY_STUDY_SESSIONS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<StudySession>>() {}.type
                val sessions: List<StudySession> = gson.fromJson(json, type)
                Log.d("SharedPrefs", "All sessions: $sessions")  // ✅ Log here
                return sessions
            } catch (e: JsonSyntaxException) {
                // Handle invalid JSON format and reset data
                Log.e("SharedPrefs", "Invalid session data. Resetting.", e)
                sharedPreferences.edit().remove(KEY_STUDY_SESSIONS).apply()  // Remove corrupted data
            } catch (e: Exception) {
                // Catch any other unexpected exceptions
                Log.e("SharedPrefs", "Error reading sessions", e)
            }
        }
        return emptyList()  // Return an empty list if no valid data is found
    }

    fun getWeeklyStudyHours(): FloatArray {
        val sessions = getStudySessions()
        val weeklyHours = FloatArray(7) { 0f }

        if (sessions.isEmpty()) return weeklyHours  // ⛑️ Safety check

        val calendar = Calendar.getInstance()
        val sevenDaysAgo = calendar.timeInMillis - (7 * 24 * 60 * 60 * 1000)

        Log.d("Tracker", "Weekly hours array: ${weeklyHours.joinToString()}")

        sessions.filter { it.date >= sevenDaysAgo }
            .forEach { session ->
                // Convert Calendar.SUNDAY (1) to array index (0–6)
                val dayIndex = (session.dayOfWeek - 1 + 7) % 7  // 💡 Ensures valid index
                weeklyHours[dayIndex] += session.durationInMinutes / 60f
            }

        return weeklyHours
    }
} 