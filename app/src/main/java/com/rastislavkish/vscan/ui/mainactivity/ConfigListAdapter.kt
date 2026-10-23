/*
* Copyright (C) 2024 Rastislav Kish
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, version 3.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.rastislavkish.vscan.ui.mainactivity

import android.content.Context

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.rastislavkish.vscan.R
import com.rastislavkish.vscan.core.BrokenConfig
import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager

class ConfigListAdapter(context: Context, configSorter: ConfigSorter): RecyclerView.Adapter<ConfigListAdapter.ConfigViewHolder>() {

    private val configManager=ConfigManager.getInstance(context)
    private val configSorter=configSorter
    private var configList=configManager.getList()
    // Listed after the configs
    private var brokenList=listOf<BrokenConfig>()
    private var filter=""
    private var itemClickListener: ((Config) -> Unit)?=null
    private var itemLongClickListener: ((Config) -> Unit)?=null
    private var brokenItemClickListener: ((BrokenConfig) -> Unit)?=null

    class ConfigViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val itemTextView: TextView=view.findViewById(R.id.itemTextView)

        init {
            itemTextView.setOnClickListener(this::itemTextView_click)
            itemTextView.setOnLongClickListener(this::itemTextView_longClick)
            }

        private var config: Config?=null
        private var broken: BrokenConfig?=null

        fun bind(config: Config) {
            this.config=config
            broken=null

            itemTextView.text=config.name
            }
        fun bind(broken: BrokenConfig) {
            this.broken=broken
            config=null

            itemTextView.text="${broken.id}, can't be read"
            }

        fun itemTextView_click(view: View) {
            val adapter=bindingAdapter as ConfigListAdapter? ?: return
            config?.let { adapter.onItemClick(it) }
            broken?.let { adapter.onBrokenItemClick(it) }
            }
        fun itemTextView_longClick(view: View): Boolean {
            config?.let { (bindingAdapter as ConfigListAdapter?)?.onItemLongClick(it) }
            return true
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val view=LayoutInflater.from(parent.context)
        .inflate(R.layout.config_list_item, parent, false)

        return ConfigViewHolder(view)
        }
    override fun onBindViewHolder(viewHolder: ConfigViewHolder, position: Int) {
        if (position<configList.size)
        viewHolder.bind(configList[position])
        else
        viewHolder.bind(brokenList[position-configList.size])
        }
    override fun getItemCount() = configList.size+brokenList.size

    fun refresh() {
        configList=configSorter.sortConfigList(configManager.getList())
        brokenList=configManager.getBrokenConfigs()

        if (!filter.isEmpty()) {
            val filterProcessed=filter.lowercase()

            configList=configList
            .filter({ item -> item.name.lowercase().contains(filterProcessed) })
            brokenList=brokenList
            .filter { it.id.lowercase().contains(filterProcessed) }
            }

        notifyDataSetChanged()
        }
    fun setFilter(filter: String) {
        this.filter=filter
        refresh()
        }
    fun setItemClickListener(listener: ((Config) -> Unit)?) {
        itemClickListener=listener
        }
    fun onItemClick(item: Config) {
        itemClickListener?.invoke(item)
        }

    fun setBrokenItemClickListener(listener: ((BrokenConfig) -> Unit)?) {
        brokenItemClickListener=listener
        }
    fun onBrokenItemClick(item: BrokenConfig) {
        brokenItemClickListener?.invoke(item)
        }

    fun setItemLongClickListener(listener: ((Config) -> Unit)?) {
        itemLongClickListener=listener
        }
    fun onItemLongClick(item: Config) {
        itemLongClickListener?.invoke(item)
        }
    }
