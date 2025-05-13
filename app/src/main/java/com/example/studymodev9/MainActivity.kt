package com.example.studymodev9

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.navigation.NavController
import androidx.navigation.ui.NavigationUI
import androidx.navigation.NavDestination
import android.util.Log
import android.content.Intent

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupNavigation()
        handleIntent(intent)
    }

    private fun setupNavigation() {
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                ?: throw IllegalStateException("NavHostFragment not found")

            navController = navHostFragment.navController
            bottomNavigationView = findViewById(R.id.bottom_navigation)
            
            // Setup the bottom navigation with the nav controller
            bottomNavigationView.setupWithNavController(navController)
            
            // Handle navigation errors
            navController.addOnDestinationChangedListener { _, destination, _ ->
                handleDestinationChange(destination)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up navigation", e)
            // Show error to user
            showNavigationError()
        }
    }

    private fun handleDestinationChange(destination: NavDestination) {
        try {
            // Update UI based on destination
            when (destination.id) {
                R.id.navigation_timer -> {
                    // Timer fragment specific setup
                }
                R.id.navigation_tracker -> {
                    // Tracker fragment specific setup
                }
                R.id.navigation_settings -> {
                    // Settings fragment specific setup
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling destination change", e)
        }
    }

    private fun handleIntent(intent: Intent?) {
        try {
            when (intent?.getBooleanExtra("open_settings", false)) {
                true -> {
                    // Navigate to settings
                    navController.navigate(R.id.navigation_settings)
                }
                else -> {
                    // Handle other intent extras if needed
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling intent", e)
        }
    }

    private fun showNavigationError() {
        // Show error dialog or message to user
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Navigation Error")
            .setMessage("There was an error setting up the navigation. Please restart the app.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
}
