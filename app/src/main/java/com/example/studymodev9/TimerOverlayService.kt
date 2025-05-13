package com.example.studymodev9

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.util.Log

class TimerOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var countDownTimer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sharedPreferencesManager: SharedPreferencesManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sharedPreferencesManager = SharedPreferencesManager.getInstance(this)
        showOverlay()
    }

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.floating_timer, null)

        setupEmergencyContactButton()
        setupEmergencyAppButton()
        setupStopButton()

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("TimerOverlay", "Error adding overlay view", e)
            stopSelf()
        }
    }


    private fun setupEmergencyContactButton() {
        overlayView?.findViewById<Button>(R.id.emergencyContactBtn)?.setOnClickListener {
            try {
                val emergencyContacts = sharedPreferencesManager.getEmergencyContacts()
                if (emergencyContacts.isNotEmpty()) {
                    val contact = emergencyContacts.first()
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${contact.number}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } else {
                    // Launch contact picker if no emergency contacts are set
                    val pickerIntent = Intent(this, ContactPickerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(pickerIntent)
                }
            } catch (e: Exception) {
                Log.e("TimerOverlay", "Error launching emergency contact", e)
            }
        }
    }

    private fun setupEmergencyAppButton() {
        overlayView?.findViewById<Button>(R.id.emergencyAppBtn)?.setOnClickListener {
            try {
                val emergencyApps = sharedPreferencesManager.getEmergencyApps()
                if (emergencyApps.isNotEmpty()) {
                    val (appName, packageName) = emergencyApps.entries.first()
                    val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    } ?: Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } else {
                    // Launch app selection if no emergency apps are set
                    val settingsIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("open_settings", true)
                    }
                    startActivity(settingsIntent)
                }
            } catch (e: Exception) {
                Log.e("TimerOverlay", "Error launching emergency app", e)
            }
        }
    }

    private fun setupStopButton() {
        val stopButton = overlayView?.findViewById<Button>(R.id.stopTimerBtn)
        stopButton?.setOnClickListener {
            Log.d("TimerOverlay", "Stop Timer Button clicked")
            stopSelf()
        }
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val timeMillis = intent.getLongExtra("TIME_MILLIS", 60000)
                startTimer(timeMillis)
            }
            "STOP" -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(timeMillis: Long) {
        val timerText = overlayView?.findViewById<TextView>(R.id.timerText)
        countDownTimer = object : CountDownTimer(timeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerText?.text = formatTime(millisUntilFinished)
            }
            override fun onFinish() {
                stopSelf()
            }
        }.start()
    }

    private fun formatTime(millisUntilFinished: Long): String {
        val seconds = millisUntilFinished / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        try {
            countDownTimer?.cancel()
            countDownTimer = null

            windowManager?.removeView(overlayView)
            overlayView = null

            handler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e("TimerOverlay", "Error during cleanup", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}