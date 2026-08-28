package com.keepr.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.keepr.app.R
import com.keepr.app.manager.AppItem

class AppsAdapter(
    private var appsList: List<AppItem>,
    private var isInteractive: Boolean = true,
    private val onToggle: (AppItem, Boolean) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    private var filteredList = appsList.toMutableList()
    private var lastQuery: String = ""
    private var onlyActive: Boolean = false

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgIcon: ImageView = itemView.findViewById(R.id.imgAppIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvPkg: TextView = itemView.findViewById(R.id.tvAppPkg)
        val switch120: SwitchCompat = itemView.findViewById(R.id.switch120Hz)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_row, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = filteredList[position]
        holder.tvName.text = item.name
        holder.tvPkg.text = item.packageName

        if (item.icon != null) {
            holder.imgIcon.setImageDrawable(item.icon)
        } else {
            holder.imgIcon.setImageResource(R.drawable.ic_keepr_logo)
        }

        holder.switch120.setOnCheckedChangeListener(null)
        holder.switch120.isChecked = item.is120HzEnabled
        holder.switch120.isEnabled = isInteractive
        holder.itemView.isEnabled = isInteractive

        if (isInteractive) {
            holder.switch120.setOnCheckedChangeListener { _, isChecked ->
                item.is120HzEnabled = isChecked
                onToggle(item, isChecked)
                if (onlyActive && !isChecked) {
                    applyCurrentFilter()
                }
            }

            holder.itemView.setOnClickListener {
                holder.switch120.isChecked = !holder.switch120.isChecked
            }
        } else {
            holder.switch120.setOnCheckedChangeListener(null)
            holder.itemView.setOnClickListener(null)
        }
    }

    fun setInteractive(interactive: Boolean) {
        if (this.isInteractive != interactive) {
            this.isInteractive = interactive
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = filteredList.size

    fun getAppsList(): List<AppItem> = appsList

    fun setAllEnabled(enabled: Boolean) {
        appsList.forEach { it.is120HzEnabled = enabled }
        applyCurrentFilter()
    }

    fun updateList(newList: List<AppItem>) {
        appsList = newList
        applyCurrentFilter()
    }

    fun setFilterOptions(query: String? = null, onlyActive: Boolean? = null) {
        if (query != null) this.lastQuery = query.trim().lowercase()
        if (onlyActive != null) this.onlyActive = onlyActive
        applyCurrentFilter()
    }

    private fun applyCurrentFilter() {
        filteredList = appsList.filter { item ->
            val matchesQuery = lastQuery.isEmpty() ||
                    item.name.lowercase().contains(lastQuery) ||
                    item.packageName.lowercase().contains(lastQuery)
            val matchesActive = !onlyActive || item.is120HzEnabled
            matchesQuery && matchesActive
        }.toMutableList()
        notifyDataSetChanged()
    }
}
