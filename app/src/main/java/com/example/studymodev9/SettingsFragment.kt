package com.example.studymodev9

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.Manifest
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.SearchView
import android.util.Log

class SettingsFragment : Fragment() {
    private lateinit var overlayPermissionStatus: TextView
    private lateinit var overlayPermissionButton: Button
    private lateinit var appsLayout: LinearLayout
    private lateinit var addAppButton: Button
    private lateinit var aboutTextView: TextView
    private lateinit var phoneStatePermissionStatus: TextView
    private lateinit var phoneStatePermissionButton: Button

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    private val PHONE_STATE_PERMISSION_REQUEST_CODE = 1237

    private val maxApps = 2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Initialize permission views
        overlayPermissionStatus = view.findViewById(R.id.overlayPermissionStatus)
        overlayPermissionButton = view.findViewById(R.id.overlayPermissionButton)
        phoneStatePermissionStatus = view.findViewById(R.id.phoneStatePermissionStatus)
        phoneStatePermissionButton = view.findViewById(R.id.phoneStatePermissionButton)

        // Initialize emergency features views
        appsLayout = view.findViewById(R.id.appsLayout)
        addAppButton = view.findViewById(R.id.addAppButton)
        aboutTextView = view.findViewById(R.id.aboutTextView)

        updatePermissionStatus()
        setupPermissionButtons()
        setupEmergencyFeatures()
        setupAboutSection()

        return view
    }

    private fun setupEmergencyFeatures() {
        addAppButton.setOnClickListener {
            val currentAppCount = appsLayout.childCount
            if (currentAppCount < maxApps) {
                showAppSelectionDialog()
            } else {
                // Show a more informative message with current limit
                val message = "Maximum $maxApps emergency apps allowed. Please remove an existing app before adding a new one."
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                
                // Highlight existing apps briefly to draw attention
                highlightExistingApps()
            }
        }
    }

    private fun highlightExistingApps() {
        for (i in 0 until appsLayout.childCount) {
            val appView = appsLayout.getChildAt(i)
            // Create highlight animation
            val originalBackground = appView.background
            appView.setBackgroundColor(android.graphics.Color.argb(50, 255, 165, 0)) // Semi-transparent orange
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                appView.background = originalBackground
            }, 500) // Reset after 500ms
        }
    }

    private fun showAppSelectionDialog() {
        val packageManager = requireContext().packageManager
        val dialog = AlertDialog.Builder(requireContext()).create()
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_app_selection, null)
        
        // Initialize views first
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.appsRecyclerView)
        val searchView = dialogView.findViewById<SearchView>(R.id.searchView)
        val okButton = dialogView.findViewById<Button>(R.id.buttonOK)
        
        // Set up RecyclerView with proper layout manager immediately
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)

        // Create adapter with empty list first
        var selectedApp: AppSelectionAdapter.AppInfo? = null
        val adapter = AppSelectionAdapter(mutableListOf()) { app ->
            selectedApp = app
            okButton.visibility = View.VISIBLE
        }
        recyclerView.adapter = adapter

        // Keep reference to full list for filtering
        var fullAppList = listOf<AppSelectionAdapter.AppInfo>()

        // Show dialog immediately while loading apps in background
        dialog.setView(dialogView)
        dialog.show()
        
        // Load apps in background
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            // Known payment app package names
            val paymentAppPackages = listOf(
                "com.google.android.apps.nbu.paisa.user",
                "com.phonepe.app",
                "net.one97.paytm",
                "com.amazon.mShop.android.shopping",
                "com.mobikwik_new",
                "com.freecharge.android",
                "com.axis.mobile",
                "com.hdfcbank.hdfcpay",
                "com.sbi.SBIFreedomPlus",
                "com.icicibank.pockets",
                "com.bhimupi",
                "com.airtel.paymentsbank",
                "com.payumoney",
                "com.olacabs.customer",
                "com.jio.myjio",
                "com.truecaller.payments",
                "com.razorpay.app",
                "com.instamojo.android",
                "com.cashfree.pg",
                "com.paytm.payments.lite"
            )

            // Get installed apps and process them
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            fullAppList = installedApps
                .asSequence()
                .filter { appInfo ->
                    try {
                        packageManager.getLaunchIntentForPackage(appInfo.packageName) != null &&
                        appInfo.packageName != requireContext().packageName
                    } catch (e: Exception) {
                        false
                    }
                }
                .map { appInfo ->
                    val packageName = appInfo.packageName
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val isPaymentApp = paymentAppPackages.contains(packageName) ||
                            appName.lowercase().run {
                                contains("pay") || contains("upi") || contains("bank") ||
                                contains("wallet") || contains("money") || contains("cash") ||
                                contains("card") || contains("credit") || contains("debit") ||
                                contains("transfer") || contains("payment") || contains("finance") ||
                                contains("financial")
                            }

                    AppSelectionAdapter.AppInfo(
                        packageName = packageName,
                        appName = appName,
                        icon = packageManager.getApplicationIcon(appInfo),
                        isPaymentApp = isPaymentApp
                    )
                }
                .sortedWith(compareByDescending<AppSelectionAdapter.AppInfo> { it.isPaymentApp }
                    .thenBy { it.appName })
                .toList()

            // Update adapter on main thread
            recyclerView.post {
                adapter.updateApps(fullAppList)
            }
        }

        // Set up search functionality
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    adapter.updateApps(fullAppList)
                } else {
                    val filteredApps = fullAppList.filter { 
                        it.appName.contains(newText, ignoreCase = true) 
                    }
                    adapter.updateApps(filteredApps)
                }
                return true
            }
        })

        // Set up OK button
        okButton.setOnClickListener {
            selectedApp?.let { app ->
                addAppView(app.appName, app.packageName)
                dialog.dismiss()
            }
        }

        // Set dialog width to 90% of screen width
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupAboutSection() {
        aboutTextView.setOnClickListener {
            Toast.makeText(requireContext(), "StudyMode V9 - Focus Timer with Emergency Features", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        loadSavedContactsAndApps()
    }

    private fun updatePermissionStatus() {
        // Check overlay permission
        if (Settings.canDrawOverlays(requireContext())) {
            overlayPermissionStatus.text = "Granted"
            overlayPermissionStatus.setTextColor(Color.GREEN)
            overlayPermissionButton.text = "Granted"
            overlayPermissionButton.isEnabled = false
        } else {
            overlayPermissionStatus.text = "Not Granted"
            overlayPermissionStatus.setTextColor(Color.RED)
            overlayPermissionButton.text = "Grant"
            overlayPermissionButton.isEnabled = true
        }

        // Check phone state permission
        if (requireContext().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            phoneStatePermissionStatus.text = "Granted"
            phoneStatePermissionStatus.setTextColor(Color.GREEN)
            phoneStatePermissionButton.text = "Granted"
            phoneStatePermissionButton.isEnabled = false
        } else {
            phoneStatePermissionStatus.text = "Not Granted"
            phoneStatePermissionStatus.setTextColor(Color.RED)
            phoneStatePermissionButton.text = "Grant"
            phoneStatePermissionButton.isEnabled = true
        }
    }

    private fun setupPermissionButtons() {
        overlayPermissionButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }


        phoneStatePermissionButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), PHONE_STATE_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            updatePermissionStatus()
        }
    }

    private fun addAppView(appName: String, packageName: String) {
        try {
            // Check for duplicates first
            for (i in 0 until appsLayout.childCount) {
                val container = appsLayout.getChildAt(i) as? LinearLayout
                val textView = container?.findViewById<TextView>(android.R.id.text1)
                if (textView?.text?.toString() == appName) {
                    Toast.makeText(requireContext(), "This app is already added", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            // Check maximum limit
            if (appsLayout.childCount >= maxApps) {
                Toast.makeText(requireContext(), "Maximum $maxApps emergency apps allowed", Toast.LENGTH_SHORT).show()
                return
            }

            // Proceed with adding the app
            val appContainer = createAppContainer(appName, packageName)
            appsLayout.addView(appContainer)

            // Save to SharedPreferences
            SharedPreferencesManager.getInstance(requireContext()).saveEmergencyApp(appName, packageName)

            // Show success message
            Toast.makeText(requireContext(), "$appName added as emergency app", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Error adding app view", e)
            Toast.makeText(requireContext(), "Error adding app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createAppContainer(appName: String, packageName: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 8, 16, 8)

            // Add the app info view
            addView(createAppInfoView(appName, packageName))
            
            // Add the delete button
            addView(createDeleteButton(this, appName))
        }
    }

    private fun createAppInfoView(appName: String, packageName: String): TextView {
        return TextView(requireContext()).apply {
            text = appName
            id = android.R.id.text1 // Set ID for finding view later
            try {
                val appIcon = requireContext().packageManager.getApplicationIcon(packageName)
                setCompoundDrawablesWithIntrinsicBounds(appIcon, null, null, null)
            } catch (e: PackageManager.NameNotFoundException) {
                setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_share, 0, 0, 0)
            }
            compoundDrawablePadding = 16
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setOnClickListener {
                try {
                    val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    } ?: Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error launching app: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createDeleteButton(container: LinearLayout, appName: String): ImageButton {
        return ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(16, 16, 16, 16)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.RED)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                marginStart = 16
            }
            setOnClickListener {
                showDeleteConfirmationDialog(container, appName)
            }
        }
    }

    private fun showDeleteConfirmationDialog(container: LinearLayout, appName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Emergency App")
            .setMessage("Are you sure you want to remove $appName from emergency apps?")
            .setPositiveButton("Remove") { _, _ ->
                try {
                    appsLayout.removeView(container)
                    SharedPreferencesManager.getInstance(requireContext()).removeEmergencyApp(appName)
                    Toast.makeText(requireContext(), "$appName removed from emergency apps", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("SettingsFragment", "Error removing app", e)
                    Toast.makeText(requireContext(), "Error removing app: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadSavedContactsAndApps() {
        // Clear existing views
        appsLayout.removeAllViews()

        // Load and display saved apps
        val savedApps = SharedPreferencesManager.getInstance(requireContext()).getEmergencyApps()
        savedApps.forEach { (appName, packageName) ->
            addAppView(appName, packageName)
        }
    }
}
