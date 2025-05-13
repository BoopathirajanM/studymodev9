package com.example.studymodev9

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class TimerFragment : Fragment() {
    private lateinit var startButton: Button
    private lateinit var hourPicker: NumberPicker
    private lateinit var minutePicker: NumberPicker
    private lateinit var timeDisplay: TextView
    private lateinit var viewModel: TimerViewModel
    private val handler = Handler(Looper.getMainLooper())
    private var pendingTimerStart = false
    private lateinit var sharedPreferencesManager: SharedPreferencesManager
    private lateinit var motivationText: TextView


    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[TimerViewModel::class.java]



        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (Settings.canDrawOverlays(requireContext())) {
                Toast.makeText(requireContext(), "Overlay permission granted", Toast.LENGTH_SHORT).show()
                if (pendingTimerStart) {
                    startTimerService(viewModel.savedMinutes)
                    pendingTimerStart = false
                }
            } else {
                Toast.makeText(requireContext(), "Overlay permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_timer, container, false)

        startButton = view.findViewById(R.id.startButton)
        hourPicker = view.findViewById(R.id.hourPicker)
        minutePicker = view.findViewById(R.id.minutePicker)
        timeDisplay = view.findViewById(R.id.timeDisplay)
        motivationText = view.findViewById(R.id.motivationText)

        setupNumberPickers()
        setupStartButton()
        sharedPreferencesManager = SharedPreferencesManager.getInstance(requireContext())
        restoreState()

        return view
    }

    private fun setupNumberPickers() {
        hourPicker.apply {
            minValue = 0
            maxValue = 23
            value = viewModel.savedHours
        }

        minutePicker.apply {
            minValue = 0
            maxValue = 59
            value = viewModel.savedMinutes % 60
        }

        val updateDisplay = { _: NumberPicker, _: Int, _: Int ->
            val hours = hourPicker.value
            val minutes = minutePicker.value
            val totalMinutes = hours * 60 + minutes

            val message = "🎯 Let's get focused!\nReady for %02d hour(s) & %02d minute(s)?".format(hours, minutes)

            timeDisplay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    timeDisplay.text = message
                    timeDisplay.animate().alpha(1f).setDuration(150).start()
                }.start()

            val motivation = when {
                totalMinutes >= 360 -> "🧘‍♂️ Knowledge Monk Mode"
                totalMinutes >= 300 -> "🧠 Master Mode"
                totalMinutes >= 240 -> "🏆 Elite Focus"
                totalMinutes >= 180 -> "🔥 In the Zone"
                totalMinutes >= 120 -> "🚀 Leveling Up"
                totalMinutes >= 60  -> "📚 Study Stronger"
                totalMinutes >= 30  -> "✅ Good Start"
                else -> ""
            }

            motivationText.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    motivationText.text = motivation
                    motivationText.animate().alpha(1f).setDuration(150).start()
                }.start()

            viewModel.savedHours = hours
            viewModel.savedMinutes = minutes

        }



        hourPicker.setOnValueChangedListener(updateDisplay)
        minutePicker.setOnValueChangedListener(updateDisplay)
        updateDisplay(hourPicker, 0, 0)
    }

    private fun setupStartButton() {
        startButton.setOnClickListener {
            val hours = hourPicker.value
            val minutes = minutePicker.value
            val totalMinutes = hours * 60 + minutes

            if (totalMinutes <= 0) {
                Toast.makeText(requireContext(), "Please select a valid time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show confirmation alert
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Start Timer")
                .setMessage("Let's Focus Study/Work!\nDo you want to start the timer for\n${String.format("%02d hr : %02d min", hours, minutes)}?")
                .setPositiveButton("Yes") { _, _ ->
                    if (!Settings.canDrawOverlays(requireContext())) {
                        pendingTimerStart = true
                        requestOverlayPermission()
                    } else {
                        startTimerService(totalMinutes)
                    }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }


    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error requesting overlay permission: ${e.message}", 
                Toast.LENGTH_LONG).show()
        }
    }

    private fun startTimerService(totalMinutes: Int) {
        if (totalMinutes > 0) {
            try {
                // Stop any existing timer service
                requireContext().stopService(Intent(requireContext(), TimerService::class.java))
                
                // Start new timer service after a short delay
                handler.postDelayed({
                    try {
                        val serviceIntent = Intent(requireContext(), TimerService::class.java).apply {
                            action = "START"
                            putExtra("MINUTES", totalMinutes)
                        }
                        requireContext().startService(serviceIntent)
                        Toast.makeText(requireContext(), "Timer started", Toast.LENGTH_SHORT).show()
                        val studyHours = totalMinutes / 60f
                        val dayIndex = getCurrentDayIndex()
                        sharedPreferencesManager.addStudyHoursToDay(dayIndex, studyHours)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error starting timer: ${e.message}", 
                            Toast.LENGTH_SHORT).show()
                    }
                }, 100)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error starting timer: ${e.message}", 
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreState() {
        if (viewModel.savedMinutes > 0) {
            hourPicker.value = viewModel.savedHours
            minutePicker.value = viewModel.savedMinutes % 60
            timeDisplay.text = String.format("%02d:%02d", viewModel.savedHours, viewModel.savedMinutes % 60)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
    }
}

class TimerViewModel : androidx.lifecycle.ViewModel() {
    var savedHours: Int = 0
    var savedMinutes: Int = 0
}

private fun getCurrentDayIndex(): Int {
    val calendar = java.util.Calendar.getInstance()
    return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> 0
        java.util.Calendar.TUESDAY -> 1
        java.util.Calendar.WEDNESDAY -> 2
        java.util.Calendar.THURSDAY -> 3
        java.util.Calendar.FRIDAY -> 4
        java.util.Calendar.SATURDAY -> 5
        java.util.Calendar.SUNDAY -> 6
        else -> 0
    }
}
