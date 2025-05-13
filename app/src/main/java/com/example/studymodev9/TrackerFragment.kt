package com.example.studymodev9

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter

class TrackerFragment : Fragment() {

    private lateinit var barChart: BarChart
    private lateinit var sharedPreferencesManager: SharedPreferencesManager

    // Define the quotes list
    private val quotes = listOf(
        "Small progress is still progress.",
        "One day or day one. You decide.",
        "Focus on the step in front of you, not the whole staircase.",
        "Discipline beats motivation.",
        "You don’t have to be great to start, but you have to start to be great.",
        "Push yourself, because no one else is going to do it for you.",
        "Done is better than perfect.",
        "Success is the sum of small efforts repeated daily.",
        "Stay consistent. It pays off.",
        "The future depends on what you do today. — Mahatma Gandhi"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_tracker, container, false)
        barChart = view.findViewById(R.id.weeklyBarChart)
        sharedPreferencesManager = SharedPreferencesManager.getInstance(requireContext())

        // Set a random quote
        val quoteTextView: TextView = view.findViewById(R.id.quoteTextView)
        val randomQuote = quotes.random()  // Get a random quote from the list
        quoteTextView.text = "\"$randomQuote\""

        val fadeIn = ObjectAnimator.ofFloat(quoteTextView, "alpha", 0f, 1f)
        fadeIn.duration = 1000 // 1 second for fade-in
        fadeIn.start()

        setupBarChart()
        loadChartData()

        return view
    }


    private fun setupBarChart() {
        barChart.description.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setDrawBarShadow(false)
        barChart.setDrawValueAboveBar(true)
        barChart.setPinchZoom(false)
        barChart.isDoubleTapToZoomEnabled = false
        barChart.legend.isEnabled = false

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.valueFormatter = IndexAxisValueFormatter(arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))

        val leftAxis = barChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f // start at 0
        leftAxis.axisMaximum = 6f // or dynamically calculate from max in `weeklyHours`

        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()}h"
            }
        }

        barChart.axisRight.isEnabled = false // Hide right axis
    }

    private fun loadChartData() {
        val weeklyHours = sharedPreferencesManager.getWeeklyStudyHours()
        val entries = ArrayList<BarEntry>()
        
        weeklyHours.forEachIndexed { index, hours ->
            entries.add(BarEntry(index.toFloat(), hours))
        }
        if (entries.size != 7) {
            Log.w("TrackerFragment", "Expected 7 days, got ${entries.size}")
        }

        val dataSet = BarDataSet(entries, "Weekly Study Hours")
        
        // Use colors from your blue palette
        context?.let {
            val context = requireContext() // Safe since fragment is attached
            dataSet.colors = listOf(
                ContextCompat.getColor(context, R.color.blue_darkest),
                ContextCompat.getColor(context, R.color.blue_dark),
                ContextCompat.getColor(context, R.color.blue_medium),
                ContextCompat.getColor(context, R.color.blue_bright),
                ContextCompat.getColor(context, R.color.blue_lightest),
                ContextCompat.getColor(context, R.color.blue_dark),
                ContextCompat.getColor(context, R.color.blue_medium)
            )
        }
        
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 12f

        val barData = BarData(dataSet)
        barData.barWidth = 0.6f // Adjust bar width as needed

        barData.setValueFormatter(object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%.2f", value)
            }
        })


        barChart.data = barData
        barChart.invalidate() // refresh chart

        barChart.animateY(1000)
    }

    override fun onResume() {
        super.onResume()
        loadChartData() // Refresh data when fragment becomes visible
    }
} 