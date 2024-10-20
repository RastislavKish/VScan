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

package com.rastislavkish.vscan.ui.configselectionactivity

import android.content.Context

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.rastislavkish.vscan.R
import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager

class ConfigListAdapter(context: Context): RecyclerView.Adapter<ConfigListAdapter.ConfigViewHolder>() {

    private val configManager=ConfigManager.getInstance(context)
    private var configList=configManager.getList()
    private var filter=""
    private var itemClickListener: ((Config) -> Unit)?=null

    class ConfigViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val itemTextView: TextView=view.findViewById(R.id.itemTextView)

        init {
            itemTextView.setOnClickListener(this::itemTextView_click)
            }

        private var config: Config?=null

        fun bind(config: Config) {
            this.config=config

            itemTextView.text=config.name
            }

        fun itemTextView_click(view: View) {
            (bindingAdapter as ConfigListAdapter?)?.onItemClick(config!!)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val view=LayoutInflater.from(parent.context)
        .inflate(R.layout.config_list_item, parent, false)

        return ConfigViewHolder(view)
        }
    override fun onBindViewHolder(viewHolder: ConfigViewHolder, position: Int) {
        viewHolder.bind(configList[position])
        }
    override fun getItemCount() = configList.size

    fun refresh() {
        configList=configManager.getList()

        if (!filter.isEmpty()) {
            val filterProcessed=filter.lowercase()

            configList=configList
            .filter({ item -> item.name.lowercase().contains(filterProcessed) })
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

    }
