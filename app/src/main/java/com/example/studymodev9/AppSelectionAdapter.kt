package com.example.studymodev9

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppSelectionAdapter(
    private val items: MutableList<Any>,  // Changed from List<AppInfo> to MutableList<Any> to support both AppInfo and String header types
    private val onAppSelected: (AppInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedPosition = -1

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
    }

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable,
        val isPaymentApp: Boolean = true
    )

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view.findViewById(android.R.id.text1)
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is String -> TYPE_HEADER
            is AppInfo -> TYPE_APP
            else -> throw IllegalArgumentException("Invalid item type at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        android.util.Log.d("AppSelectionAdapter", "Creating view holder for type: $viewType")
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
                view.setBackgroundColor(parent.context.getColor(android.R.color.darker_gray))
                view.findViewById<TextView>(android.R.id.text1).apply {
                    setTextColor(parent.context.getColor(android.R.color.white))
                    setPadding(32, 16, 16, 16)
                }
                HeaderViewHolder(view)
            }
            TYPE_APP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app_selection, parent, false)
                AppViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            android.util.Log.d("AppSelectionAdapter", "Binding view holder at position: $position")
            when (val item = items[position]) {
                is String -> {
                    if (holder is HeaderViewHolder) {
                        holder.headerText.text = item
                        android.util.Log.d("AppSelectionAdapter", "Binding header: $item")
                    }
                }
                is AppInfo -> {
                    if (holder is AppViewHolder) {
                        holder.appIcon.setImageDrawable(item.icon)
                        holder.appName.text = item.appName
                        holder.itemView.isSelected = position == selectedPosition
                        android.util.Log.d("AppSelectionAdapter", "Binding app: ${item.appName}")

                        holder.itemView.setOnClickListener { 
                            try {
                                val previousPosition = selectedPosition
                                selectedPosition = holder.adapterPosition
                                notifyItemChanged(previousPosition)
                                notifyItemChanged(selectedPosition)
                                onAppSelected(item)
                            } catch (e: Exception) {
                                android.util.Log.e("AppSelectionAdapter", "Error handling app selection: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppSelectionAdapter", "Error binding view holder: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int {
        android.util.Log.d("AppSelectionAdapter", "Getting item count: ${items.size}")
        return items.size
    }
    
    // Method to update the list of apps
    fun updateApps(newApps: List<AppInfo>) {
        android.util.Log.d("AppSelectionAdapter", "Updating apps, new count: ${newApps.size}")
        selectedPosition = -1  // Reset selection when updating apps
        items.clear()
        items.addAll(newApps)
        notifyDataSetChanged()
    }
} 