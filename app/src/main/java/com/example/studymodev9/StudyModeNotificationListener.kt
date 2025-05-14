package com.example.studymodev9

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class StudyModeNotificationListener : NotificationListenerService() {
    private lateinit var sharedPreferencesManager: SharedPreferencesManager

    override fun onCreate() {
        super.onCreate()
        sharedPreferencesManager = SharedPreferencesManager.getInstance(this)
        createNotificationChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val blockedApps = sharedPreferencesManager.getBlockedApps()
            
            // Check if the notification is from a blocked app
            if (blockedApps.containsValue(sbn.packageName)) {
                // Cancel the notification
                cancelNotification(sbn.key)
                Log.d("NotificationListener", "Blocked notification from: ${sbn.packageName}")
            }
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error processing notification", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Study Mode"
            val descriptionText = "Notifications for Study Mode app"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("study_mode_channel", name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
} 