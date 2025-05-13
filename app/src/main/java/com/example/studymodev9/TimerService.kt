package com.example.studymodev9

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import android.app.Application
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.provider.Settings
import android.provider.ContactsContract
import android.util.Log
import android.content.BroadcastReceiver
import android.content.IntentFilter
import java.util.Locale
import java.lang.ref.WeakReference

class TimerService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var timerText: TextView? = null
    private var circularProgress: ProgressBar? = null
    private var totalTimeInSeconds: Int = 0
    private var remainingTimeInSeconds: Int = 0
    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private var isTimerRunning = false
    private val CHANNEL_ID = "StudyTimerChannel"
    private val NOTIFICATION_ID = 1
    private var params: WindowManager.LayoutParams? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var returnToCallBtn: Button? = null
    private var isInCall = false
    private var phoneNumber: String? = null
    private var endCallBtn: Button? = null
    private var telephonyManager: TelephonyManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastAppPackageName: String? = null
    private val activityCheckHandler = Handler(Looper.getMainLooper())
    private val activityCheckRunnable = object : Runnable {
        override fun run() {
            checkForegroundAppAndRestoreTimer()
            activityCheckHandler.postDelayed(this, 1000)
        }
    }
    
    // Add the missing variables for call screen checking
    private var callScreenCheckHandler: Handler? = null
    private var callScreenCheckRunnable: Runnable? = null

    private val phoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, incomingNumber: String) {
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (isInCall) {
                        isInCall = false
                        Handler(Looper.getMainLooper()).postDelayed({
                            restoreFloatingView()
                            Log.d("TimerService", "Call ended, restored timer")
                            lastAppPackageName = null
                            phoneNumber = null
                            stopCallScreenCheck()
                        }, 1500)
                    }
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    isInCall = true
                    Log.d("TimerService", "Call is now active")
                    phoneNumber?.let { number ->
                        lastAppPackageName = "phone-$number"
                    } ?: run {
                        lastAppPackageName = "phone-active"
                    }
                    startCallScreenCheck()
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.d("TimerService", "Call is ringing (incoming or outgoing)")
                }
            }
        }
    }

    private val callBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.studymodev9.CALL_STARTED") {
                val phoneNumber = intent.getStringExtra("PHONE_NUMBER")
                val contactName = intent.getStringExtra("CONTACT_NAME")
                isInCall = true
                this@TimerService.phoneNumber = phoneNumber
                lastAppPackageName = "phone-$phoneNumber"
                Log.d("TimerService", "Received call started broadcast: calling $contactName ($phoneNumber)")
                startCallScreenCheck()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setupForegroundService()
        acquireWakeLock()
        setupTelephonyManager()
        registerCallReceiver()
        setupFloatingView()
        activityCheckHandler.postDelayed(activityCheckRunnable, 1000)
    }

    private fun setupForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun setupTelephonyManager() {
        try {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            } else {
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Error registering phone listener", e)
        }
    }

    private fun registerCallReceiver() {
        try {
            val filter = IntentFilter("com.example.studymodev9.CALL_STARTED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(callBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(callBroadcastReceiver, filter)
            }
            Log.d("TimerService", "Call receiver registered successfully")
        } catch (e: Exception) {
            Log.e("TimerService", "Error registering call receiver", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "StudyTimer::TimerWakeLock"
            ).apply {
                acquire(10*60*1000L) // 10 minutes timeout
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Error acquiring wake lock", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        try {
            // Cancel timer
            countDownTimer?.cancel()
            countDownTimer = null

            // Remove floating view
            try {
                if (floatingView != null && windowManager != null) {
                    windowManager?.removeView(floatingView)
                }
            } catch (e: Exception) {
                Log.e("TimerService", "Error removing floating view", e)
            }
            floatingView = null

            // Unregister phone state listener
            try {
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            } catch (e: Exception) {
                Log.e("TimerService", "Error unregistering phone listener", e)
            }
            telephonyManager = null

            // Unregister broadcast receiver
            try {
                unregisterReceiver(callBroadcastReceiver)
            } catch (e: Exception) {
                Log.e("TimerService", "Error unregistering receiver", e)
            }

            // Stop activity check
            try {
                activityCheckHandler.removeCallbacks(activityCheckRunnable)
            } catch (e: Exception) {
                Log.e("TimerService", "Error stopping activity check", e)
            }

            // Stop call screen check
            try {
                stopCallScreenCheck()
            } catch (e: Exception) {
                Log.e("TimerService", "Error stopping call screen check", e)
            }

            // Release wake lock
            try {
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
            } catch (e: Exception) {
                Log.e("TimerService", "Error releasing wake lock", e)
            }
            wakeLock = null

            // Cancel coroutines
            try {
                serviceScope.cancel()
            } catch (e: Exception) {
                Log.e("TimerService", "Error canceling coroutines", e)
            }

        } catch (e: Exception) {
            Log.e("TimerService", "Error during cleanup", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Study Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the study timer status"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Study Timer")
            .setContentText("Timer is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupFloatingView() {
        try {
            // Initialize windowManager if it's null
            if (windowManager == null) {
                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            }

            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.FILL
            }

            val inflater = LayoutInflater.from(this)
            floatingView = inflater.inflate(R.layout.floating_timer, null)

            timerText = floatingView?.findViewById(R.id.timerText)
            circularProgress = floatingView?.findViewById(R.id.circularProgress)
            val stopTimerBtn = floatingView?.findViewById<Button>(R.id.stopTimerBtn)
            val emergencyContactBtn = floatingView?.findViewById<Button>(R.id.emergencyContactBtn)
            val emergencyAppBtn = floatingView?.findViewById<Button>(R.id.emergencyAppBtn)
            returnToCallBtn = floatingView?.findViewById(R.id.returnToCallBtn)
            endCallBtn = floatingView?.findViewById(R.id.endCallBtn)

            // Set initial progress
            circularProgress?.max = 100
            circularProgress?.progress = 100

            stopTimerBtn?.setOnClickListener {
                stopSelf()
            }

            emergencyContactBtn?.setOnClickListener {
                try {
                    showContactNavigationDialog()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error accessing contacts: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            emergencyAppBtn?.setOnClickListener {
                try {
                    showEmergencyAppsDialog()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error showing apps: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            returnToCallBtn?.setOnClickListener {
                try {
                    returnToCallUI()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error returning to call: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            endCallBtn?.setOnClickListener {
                try {
                    endCall()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error ending call: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // Make sure we don't add the view if it's already added
            if (floatingView?.parent == null) {
                try {
                    windowManager?.addView(floatingView, params)
                    Log.d("TimerService", "Added floating view to window")
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error showing overlay: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("TimerService", "Floating view already added")
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Error in setupFloatingView", e)
            Toast.makeText(this, "Error setting up floating view: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showContactNavigationDialog() {
        try {
            val confirmDialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_contact_confirmation, null)
                            
            val confirmDialogParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            // No need to set texts - they're already in the layout            
            confirmDialogView.findViewById<Button>(R.id.confirmButton).setOnClickListener {
                try {
                    // First remove the dialog
                    try {
                        windowManager?.removeView(confirmDialogView)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    // Make floating view temporarily invisible to allow contacts to be seen
                    try {
                        // Completely hide the overlay by removing it temporarily
                        if (floatingView != null && windowManager != null) {
                            windowManager?.removeView(floatingView)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // If removing fails, at least make it invisible
                        floatingView?.visibility = View.GONE
                    }
                    
                    // Store info that we're in contact picking mode
                    lastAppPackageName = "contacts-picking"
                    
                    // Launch the ContactPickerActivity which will handle the full flow
                    val pickerIntent = Intent(this@TimerService, ContactPickerActivity::class.java)
                    pickerIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(pickerIntent)
                    
                    Log.d("TimerService", "Started ContactPickerActivity")
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@TimerService, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Restore the floating view if there was an error
                    restoreFloatingView()
                }
            }

            confirmDialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
                try {
                    windowManager?.removeView(confirmDialogView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            try {
                // Remove any existing dialog views first to prevent window leaks
                try {
                    val rootView = floatingView?.rootView
                    if (rootView != null) {
                        val viewGroup = rootView as? ViewGroup
                        if (viewGroup != null) {
                            for (i in 0 until viewGroup.childCount) {
                                val child = viewGroup.getChildAt(i)
                                if (child.id != floatingView?.id && child != floatingView) {
                                    try { windowManager?.removeView(child) } catch (e: Exception) { }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Just continue if cleanup fails
                }
                
                windowManager?.addView(confirmDialogView, confirmDialogParams)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error showing confirmation dialog: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error preparing contact navigation: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmergencyAppsDialog() {
        try {
            val apps = SharedPreferencesManager.getInstance(this).getEmergencyApps()
            if (apps.isEmpty()) {
                Toast.makeText(this, "No emergency apps saved", Toast.LENGTH_SHORT).show()
                return
            }

            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_emergency_apps, null)
            val appsContainer = dialogView.findViewById<LinearLayout>(R.id.appsContainer)
            val closeButton = dialogView.findViewById<ImageView>(R.id.closeButton)
            
            // Add content description for accessibility
            closeButton.contentDescription = "Close emergency apps dialog"
            
            // Add app items to the container
            apps.forEach { (appName, packageName) ->
                try {
                    val appItemView = TextView(this).apply {
                        text = appName
                        textSize = 16f
                        setTextColor(resources.getColor(android.R.color.white, theme))
                        setPadding(16, 16, 16, 16)
                        
                        try {
                            val appIcon = packageManager.getApplicationIcon(packageName)
                            setCompoundDrawablesWithIntrinsicBounds(appIcon, null, null, null)
                            compoundDrawablePadding = 16
                        } catch (e: PackageManager.NameNotFoundException) {
                            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_share, 0, 0, 0)
                        }
                        
                        setOnClickListener {
                            try {
                                val confirmDialogView = LayoutInflater.from(this@TimerService)
                                    .inflate(R.layout.dialog_confirm_app, null)
                                
                                val confirmDialogParams = WindowManager.LayoutParams(
                                    WindowManager.LayoutParams.WRAP_CONTENT,
                                    WindowManager.LayoutParams.WRAP_CONTENT,
                                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                                    PixelFormat.TRANSLUCENT
                                ).apply {
                                    gravity = Gravity.CENTER
                                }

                                confirmDialogView.findViewById<TextView>(R.id.messageText).text = 
                                    "Open $appName?"

                                confirmDialogView.findViewById<Button>(R.id.confirmButton).setOnClickListener {
                                    try {
                                        // First remove the dialog
                                        try {
                                            windowManager?.removeView(confirmDialogView)
                                            // Also remove the parent dialog
                                            try { windowManager?.removeView(dialogView) } catch (e: Exception) { e.printStackTrace() }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }

                                        // Make floating view temporarily invisible to allow app to be seen
                                        floatingView?.visibility = View.GONE
                                        
                                        // Store the package name being launched
                                        lastAppPackageName = packageName
                                        
                                        // Create intent for launching the app
                                val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                                   Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                   Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                } ?: Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("market://details?id=$packageName")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                        
                                        // Launch the app
                                startActivity(intent)
                                        Toast.makeText(this@TimerService, "Launching $appName...", Toast.LENGTH_SHORT).show()

                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(this@TimerService, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        floatingView?.visibility = View.VISIBLE // Ensure visibility is restored
                                    }
                                }

                                confirmDialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
                                    try {
                                        windowManager?.removeView(confirmDialogView)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }

                                try {
                                    windowManager?.addView(confirmDialogView, confirmDialogParams)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(this@TimerService, "Error showing confirmation dialog: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(this@TimerService, "Error launching app: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    
                    appsContainer.addView(appItemView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            closeButton.setOnClickListener {
                try {
                    windowManager?.removeView(dialogView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val dialogParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                // Remove any existing dialog views first to prevent window leaks
                try {
                    val rootView = floatingView?.rootView
                    if (rootView != null) {
                        val viewGroup = rootView as? ViewGroup
                        if (viewGroup != null) {
                            for (i in 0 until viewGroup.childCount) {
                                val child = viewGroup.getChildAt(i)
                                if (child.id != floatingView?.id && child != floatingView) {
                                    try { windowManager?.removeView(child) } catch (e: Exception) { }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Just continue if cleanup fails
                }
                
                windowManager?.addView(dialogView, dialogParams)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error showing apps dialog: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error showing emergency apps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTimer() {
        countDownTimer?.cancel() // Cancel any existing timer first
        countDownTimer = object : CountDownTimer((totalTimeInSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeInSeconds = (millisUntilFinished / 1000).toInt()
                val minutes = remainingTimeInSeconds / 60
                val seconds = remainingTimeInSeconds % 60
                timerText?.text = String.format("%02d:%02d", minutes, seconds)
                
                // Update circular progress
                val progress = (remainingTimeInSeconds.toFloat() / totalTimeInSeconds.toFloat() * 100).toInt()
                circularProgress?.progress = progress
            }

            override fun onFinish() {
                timerText?.text = "00:00"
                circularProgress?.progress = 0
                onTimerComplete()
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            val minutes = intent.getIntExtra("MINUTES", 25)
            totalTimeInSeconds = minutes * 60
            remainingTimeInSeconds = totalTimeInSeconds
            
            // Make sure floating view is initialized and visible
            if (floatingView == null || floatingView?.parent == null) {
                setupFloatingView()
            } else {
                floatingView?.visibility = View.VISIBLE
            }
            
            startTimer()
        } else if (intent?.action == "STOP") {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun safeNavigateToSettings() {
        // Show a toast with instructions
        Toast.makeText(this, "Please grant phone call permission in settings and return to the app", Toast.LENGTH_LONG).show()
        
        // Give time for the toast to appear and be read
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // Create an intent to open settings with appropriate flags
                val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    // These flags help maintain the service while opening the settings
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                            Intent.FLAG_ACTIVITY_NO_HISTORY
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                
                // Start the settings activity
                startActivity(settingsIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error opening settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, 500) // Half-second delay to ensure UI operations complete
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun setForegroundServiceProcessPriority() {
        try {
            // This is using reflection to access hidden Android API
            // It could stop working in future Android versions
            val setProcessClass = Class.forName("android.os.Process")
            val setThreadPriority = setProcessClass.getMethod("setThreadPriority", Int::class.java)
            setThreadPriority.invoke(null, -19) // THREAD_PRIORITY_FOREGROUND
        } catch (e: Exception) {
            // Fail silently, this is an optional optimization
            e.printStackTrace()
        }
    }

    private fun forceCallScreenFocus() {
        try {
            if (phoneNumber != null) {
                // Create an intent to "view" the current call
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    
                    // Try to open the dialer with the current call
                    setClassName("com.android.server.telecom", "com.android.server.telecom.CallActivity")
                }
                
                // Add backup options for different OEMs
                val callIntent = Intent(Intent.ACTION_VIEW).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                
                // Try to launch the call activity
                try {
                    startActivity(intent)
                    Log.d("TimerService", "Attempted to restore call screen (primary method)")
                } catch (e: Exception) {
                    // Try alternative methods
                    Log.d("TimerService", "Primary call screen restoration failed, trying alternatives")
                    
                    // Try different intent approaches based on device manufacturer
                    val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
                    
                    when {
                        manufacturer.contains("samsung") -> {
                            // Samsung phone app
                            callIntent.setClassName("com.samsung.android.dialer", "com.samsung.android.dialer.CallActivity")
                        }
                        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                            // Xiaomi phone app
                            callIntent.setClassName("com.android.incallui", "com.android.incallui.InCallActivity")
                        }
                        manufacturer.contains("huawei") -> {
                            // Huawei phone app
                            callIntent.setClassName("com.android.dialer", "com.android.dialer.DialtactsActivity")
                        }
                        else -> {
                            // Generic approach
                            callIntent.setClassName("com.android.dialer", "com.android.dialer.InCallActivity")
                        }
                    }
                    
                    try {
                        startActivity(callIntent)
                        Log.d("TimerService", "Attempted to restore call screen (alternative method)")
                    } catch (e2: Exception) {
                        Log.e("TimerService", "All attempts to restore call screen failed", e2)
                    }
                }
            } else {
                Log.d("TimerService", "Cannot restore call screen - phone number unknown")
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Error trying to force call screen focus", e)
        }
    }

    private fun returnToCallUI() {
        try {
            // Make sure the timer is hidden when returning to call screen
            try {
                // Completely hide the overlay by removing it temporarily
                if (floatingView != null && windowManager != null) {
                    windowManager?.removeView(floatingView)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // If removing fails, at least make it invisible
                floatingView?.visibility = View.GONE
            }
            
            // Try the new focused method first
            forceCallScreenFocus()
            
            // Show a toast to indicate we're trying to access the call screen
            Toast.makeText(this, "Accessing call screen...", Toast.LENGTH_SHORT).show()
            
            // Don't restore the timer here - let the call state listener handle it
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error accessing call screen: ${e.message}", Toast.LENGTH_SHORT).show()
            // If there was an error, restore the floating view
            restoreFloatingView()
        }
    }

    private fun makeDirectCall(contactNumber: String) {
        phoneNumber = contactNumber
        
        // Hide the floating view completely to make call screen fully visible
        try {
            // Completely hide the overlay by removing it temporarily
            if (floatingView != null && windowManager != null) {
                windowManager?.removeView(floatingView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If removing fails, at least make it invisible
            floatingView?.visibility = View.GONE
        }
        
        // Show instruction toast
        Toast.makeText(this, "Making call...", Toast.LENGTH_SHORT).show()
        
        // Store state that we're about to make a call
        isInCall = true
        lastAppPackageName = "phone-$contactNumber"
        
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$contactNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
            
            // Use our force focus method after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                forceCallScreenFocus()
            }, 500) // Half-second delay to ensure call connects first
            
            // The call state will be handled by the PhoneStateListener
            // which will re-add the view when the call ends
        } catch (e: SecurityException) {
            Toast.makeText(this, "Error making call: ${e.message}", Toast.LENGTH_SHORT).show()
            // Restore the floating view since the call failed
            restoreFloatingView()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            // Restore the floating view since the call failed
            restoreFloatingView()
        }
    }

    private fun endCall() {
        // For Android P (API 28) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                
                // First approach: Use showInCallScreen to bring up the call UI
                telecomManager.showInCallScreen(false)
                
                // Show instructions
                Toast.makeText(this, "Tap the red button in the phone UI to end the call", Toast.LENGTH_LONG).show()
                
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // For older Android versions, try using the default dialer app
        try {
            // Launch phone app directly
            val intent = Intent(Intent.ACTION_DIAL)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            
            // Show instructions
            Toast.makeText(this, "Tap the red button in the phone UI to end the call", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            
            // Last resort: Open call settings
            try {
                val settingsIntent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(settingsIntent)
            } catch (e2: Exception) {
                e2.printStackTrace()
                Toast.makeText(this, "Unable to access phone UI", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkForegroundAppAndRestoreTimer() {
        // Don't try to restore during calls, let the phoneStateListener handle it
        if (isInCall) {
            return
        }
        
        // Only check for apps if timer is hidden and we're not in a call
        if (floatingView?.visibility == View.GONE || floatingView?.parent == null) {
            try {
                val foregroundPackage = getForegroundPackage()
                Log.d("TimerService", "Current foreground package: $foregroundPackage, Last app: $lastAppPackageName")
                
                // Special handling for contacts picking mode - we expect to transition to a call
                if (lastAppPackageName == "contacts-picking") {
                    // If in contacts app, don't restore
                    if (foregroundPackage != null && 
                        (foregroundPackage.contains("contact") || 
                         foregroundPackage.contains("people"))) {
                        return
                    }
                    
                    // If transitioning to dialer or call screen, update our state
                    if (foregroundPackage != null && 
                        (foregroundPackage.contains("dialer") || 
                         foregroundPackage.contains("phone") ||
                         foregroundPackage.contains("telecom"))) {
                        // We're now in call mode
                        isInCall = true
                        lastAppPackageName = "phone-dialing"
                        Log.d("TimerService", "Contact selection transitioned to call: $foregroundPackage")
                        return
                    }
                    
                    // If we got here, we're neither in contacts nor in call/dialer
                    // This means the user cancelled or something went wrong - restore timer
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isInCall) {
                            restoreFloatingView()
                            Log.d("TimerService", "Contact selection cancelled: $foregroundPackage")
                        }
                    }, 1000)
                    lastAppPackageName = null
                    return
                }
                
                // Special handling for regular contacts or people apps
                if (lastAppPackageName?.startsWith("contacts-") == true || 
                    (lastAppPackageName != null && 
                     (lastAppPackageName!!.contains("contact") || 
                      lastAppPackageName!!.contains("people")))
                   ) {
                      
                    // If we're still in a contacts app or dialer, don't restore
                    if (foregroundPackage != null && 
                        (foregroundPackage.contains("contact") || 
                         foregroundPackage.contains("dialer") || 
                         foregroundPackage.contains("phone") ||
                         foregroundPackage.contains("people"))) {
                        // Still in contacts - update our tracking with the actual package
                        if (lastAppPackageName == "contacts-") {
                            lastAppPackageName = foregroundPackage
                        }
                        Log.d("TimerService", "Still in contacts app, not restoring timer")
                        return
                    }
                    
                    // Check if we've just left contacts - add a delay if going to another app
                    if (foregroundPackage != null) {
                        // Wait longer before restoring to avoid flickering
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isInCall) { // Double-check we're not in a call now
                                // Make sure we're really not in a contacts app anymore
                                val currentForeground = getForegroundPackage()
                                if (currentForeground != null && !currentForeground.contains("contact") &&
                                    !currentForeground.contains("dialer") && !currentForeground.contains("phone")) {
                                    restoreFloatingView()
                                    Log.d("TimerService", "Delayed restore after contacts: $currentForeground")
                                }
                            }
                        }, 2000) // 2-second delay
                        return
                    }
                }
                
                // Only restore timer if we're no longer in the emergency app
                // and we're not in the settings screen to grant permissions
                if (foregroundPackage != null && 
                    foregroundPackage != lastAppPackageName &&
                    !foregroundPackage.contains("settings")) {
                    
                    // Restore the floating view
                    restoreFloatingView()
                    
                    // Log that we restored the timer
                    Log.d("TimerService", "Restored timer visibility - user left app: $foregroundPackage")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getForegroundPackage(): String? {
        try {
            // For Android Lollipop and above, we need to use UsageStatsManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Check if we have permission
                if (!hasUsageStatsPermission()) {
                    // Request permission via a toast notification
                    Toast.makeText(this, "Please grant usage stats permission for full functionality", 
                                   Toast.LENGTH_LONG).show()
                    requestUsageStatsPermission()
                    return null
                }
                
                // Get usage stats
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val time = System.currentTimeMillis()
                // Look for usage stats in the last 5 seconds
                val usageStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, time - 5000, time)
                
                if (usageStats != null && usageStats.isNotEmpty()) {
                    // Sort by last time used
                    var lastUsedAppPackage: String? = null
                    var lastUsedTime = 0L
                    
                    for (stat in usageStats) {
                        if (stat.lastTimeUsed > lastUsedTime) {
                            lastUsedTime = stat.lastTimeUsed
                            lastUsedAppPackage = stat.packageName
                        }
                    }
                    
                    return lastUsedAppPackage
                }
            } else {
                // Fallback for older versions
                @Suppress("DEPRECATION")
                val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningTasks = manager.getRunningTasks(1)
                if (runningTasks != null && runningTasks.isNotEmpty()) {
                    val topActivity = runningTasks[0].topActivity
                    return topActivity?.packageName
                }
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Error getting foreground package", e)
            e.printStackTrace()
        }
        return null
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    
    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun restoreFloatingView() {
        try {
            isInCall = false
            lastAppPackageName = null
            
            // If the view was removed, add it back
            if (floatingView != null && windowManager != null && floatingView?.parent == null) {
                try {
                    windowManager?.addView(floatingView, params)
                    Log.d("TimerService", "Floating view restored after call ended/failed")
                    
                    // Update timer display with current time
                    if (remainingTimeInSeconds > 0) {
                        val minutes = remainingTimeInSeconds / 60
                        val seconds = remainingTimeInSeconds % 60
                        timerText?.text = String.format("%02d:%02d", minutes, seconds)
                        
                        // Update circular progress
                        val progress = (remainingTimeInSeconds.toFloat() / totalTimeInSeconds.toFloat() * 100).toInt()
                        circularProgress?.progress = progress
                    }
                } catch (e: Exception) {
                    Log.e("TimerService", "Error restoring floating view", e)
                    // Try recreating the floating view if adding back fails
                    setupFloatingView()
                }
            } else if (floatingView != null) {
                // Otherwise just make it visible again
                floatingView?.visibility = View.VISIBLE
                Log.d("TimerService", "Made existing floating view visible")
            } else {
                // If floatingView is null, recreate it
                Log.d("TimerService", "Floating view was null, recreating it")
                setupFloatingView()
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Error in restoreFloatingView", e)
            // Last resort - recreate the whole floating view
            try {
                setupFloatingView()
            } catch (e2: Exception) {
                Log.e("TimerService", "Failed to recreate floating view", e2)
            }
        }
    }

    private fun createContactsViewIntent(): Intent {
        // This creates an intent that explicitly targets the contacts for picking a contact to call
        return Intent(Intent.ACTION_PICK).apply {
            // Set type to phone contacts specifically
            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            // Add flags to ensure it opens properly
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun onTimerComplete() {
        // Save the completed study session
        val session = StudySession(durationInMinutes = totalTimeInSeconds / 60)
        SharedPreferencesManager.getInstance(this).saveStudySession(session)
        
        // Stop the service
        stopSelf()
        
        // Show completion notification
        showCompletionNotification()
    }

    private fun showCompletionNotification() {
        try {
            // Create a notification channel for Android O and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "TIMER_COMPLETION",
                    "Timer Completion",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for completed study sessions"
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }

            // Create an intent to open the app
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )

            // Build the notification
            val notification = NotificationCompat.Builder(this, "TIMER_COMPLETION")
                .setContentTitle("Study Session Complete!")
                .setContentText("Great job! You've completed your study session.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            // Show the notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(2, notification)
            
            Log.d("TimerService", "Completion notification shown")
        } catch (e: Exception) {
            Log.e("TimerService", "Error showing completion notification", e)
        }
    }

    private fun startCallScreenCheck() {
        try {
            // First, stop any existing check
            stopCallScreenCheck()
            
            callScreenCheckHandler = Handler(Looper.getMainLooper())
            callScreenCheckRunnable = object : Runnable {
                override fun run() {
                    if (isInCall) {
                        // Check if we're in the call screen
                        val foregroundPackage = getForegroundPackage()
                        if (foregroundPackage == null || 
                            (!foregroundPackage.contains("phone") && 
                             !foregroundPackage.contains("dialer") && 
                             !foregroundPackage.contains("telecom") &&
                             !foregroundPackage.contains("incall"))) {
                            // Not in call screen - try to bring it back
                            Log.d("TimerService", "Call screen not in foreground, restoring it")
                            forceCallScreenFocus()
                        }
                        
                        // Schedule the next check
                        callScreenCheckHandler?.postDelayed(this, 3000) // Check every 3 seconds
                    } else {
                        // Call has ended, stop checking
                        stopCallScreenCheck()
                    }
                }
            }
            
            // Start the check
            callScreenCheckHandler?.postDelayed(callScreenCheckRunnable!!, 1000) // First check after 1 second
        } catch (e: Exception) {
            Log.e("TimerService", "Error starting call screen check", e)
        }
    }
    
    private fun stopCallScreenCheck() {
        try {
            callScreenCheckRunnable?.let { runnable ->
                callScreenCheckHandler?.removeCallbacks(runnable)
            }
            callScreenCheckHandler = null
            callScreenCheckRunnable = null
        } catch (e: Exception) {
            Log.e("TimerService", "Error stopping call screen check", e)
        }
    }
} 