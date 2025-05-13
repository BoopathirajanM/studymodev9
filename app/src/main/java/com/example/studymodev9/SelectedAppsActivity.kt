package com.example.studymodev9

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studymodev9.R.id.buttonOK

class SelectedAppsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppSelectionAdapter
    private lateinit var sharedPreferencesManager: SharedPreferencesManager
    private lateinit var okButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_app_selection)

        sharedPreferencesManager = SharedPreferencesManager.getInstance(this)

        okButton = findViewById(R.id.buttonOK)
        okButton.visibility = View.GONE


        val selectedApps = loadSelectedApps()

        adapter = AppSelectionAdapter(selectedApps.toMutableList()) { selectedApp ->
            Toast.makeText(this, "App selected successfully", Toast.LENGTH_SHORT).show()
            okButton.visibility = View.VISIBLE
            Log.d("SelectedAppsActivity", "Button visibility set to VISIBLE")  // Add this log

            okButton.setOnClickListener {
                finish()
            }
        }

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            okButton.visibility = View.VISIBLE
        }
    }

    private fun launchApp(app: AppSelectionAdapter.AppInfo) {
        try {
            when (app.packageName) {
                "com.google.android.apps.nbu.paisa.user" -> {
                    // Launch Google Pay
                    try {
                        // Try direct launch first
                        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                        if (intent != null) {
                            startActivity(intent)
                            return
                        }
                    } catch (e: Exception) {
                        // Fall through to other methods
                    }
                    
                    // Try UPI intent
                    try {
                        val upiIntent = Intent(Intent.ACTION_VIEW)
                        upiIntent.data = Uri.parse("upi://pay")
                        startActivity(upiIntent)
                        return
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not launch Google Pay", Toast.LENGTH_SHORT).show()
                    }
                }
                
                "com.phonepe.app" -> {
                    // Launch PhonePe
                    try {
                        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                        if (intent != null) {
                            startActivity(intent)
                            return
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not launch PhonePe", Toast.LENGTH_SHORT).show()
                    }
                }
                
                "net.one97.paytm" -> {
                    // Launch Paytm
                    try {
                        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                        if (intent != null) {
                            startActivity(intent)
                            return
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not launch Paytm", Toast.LENGTH_SHORT).show()
                    }
                }
                
                else -> {
                    // For other apps, try to launch normally
                    val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Cannot launch ${app.appName}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error launching ${app.appName}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSelectedApps(): List<AppSelectionAdapter.AppInfo> {
        val selectedPackageNames = sharedPreferencesManager.getEmergencyApps().keys.toList()
        val installedApps = mutableListOf<AppSelectionAdapter.AppInfo>()
        
        // First, add payment apps if they are installed
        val paymentApps = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm"
        )
        
        paymentApps.forEach { (packageName, appName) ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                // Check if app is installed, can be launched, and is not a system app
                if (packageManager.getLaunchIntentForPackage(packageName) != null && 
                    !isSystemApp(appInfo)) {
                    installedApps.add(
                        AppSelectionAdapter.AppInfo(
                            packageName = packageName,
                            appName = appName,
                            icon = packageManager.getApplicationIcon(appInfo),
                            isPaymentApp = true
                        )
                    )
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // App not installed, skip it
            }
        }
        
        // Then add other selected apps that are installed
        selectedPackageNames.forEach { packageName ->
            // Skip if it's already added as a payment app
            if (!paymentApps.containsKey(packageName)) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    // Only add if the app is actually installed, can be launched, and is not a system app
                    if (packageManager.getLaunchIntentForPackage(packageName) != null && 
                        !isSystemApp(appInfo)) {
                        installedApps.add(
                            AppSelectionAdapter.AppInfo(
                                packageName = packageName,
                                appName = packageManager.getApplicationLabel(appInfo).toString(),
                                icon = packageManager.getApplicationIcon(appInfo),
                                isPaymentApp = false
                            )
                        )
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    // App not installed, skip it
                }
            }
        }
        
        return installedApps
    }
    
    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        // Check if the app is a system app
        return appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }
} 