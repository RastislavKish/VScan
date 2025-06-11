/*
* Copyright (C) 2025 Rastislav Kish
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

package com.rastislavkish.vscan.ui.modelprovidermappingsactivity

import android.content.Context

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.rastislavkish.vscan.R
import com.rastislavkish.vscan.core.Provider
import com.rastislavkish.vscan.core.ProvidersManager

class MappingsListAdapter(context: Context): RecyclerView.Adapter<MappingsListAdapter.MappingViewHolder>() {

    private val providersManager=ProvidersManager.getInstance(context)
    private var mappingsList=providersManager.getAllModelProviderMappings()
    private var filter=""
    private var itemClickListener: ((Mapping) -> Unit)?=null

    class MappingViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val itemTextView: TextView=view.findViewById(R.id.itemTextView)

        init {
            itemTextView.setOnClickListener(this::itemTextView_click)
            }

        private var mapping: Mapping?=null

        fun bind(mapping: Mapping) {
            this.mapping=mapping

            val (model, provider)=mapping

            val providerName=provider?.name ?: "None"
            itemTextView.text="$model -> $providerName"
            }

        fun itemTextView_click(view: View) {
            (bindingAdapter as MappingsListAdapter?)?.onItemClick(mapping!!)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MappingViewHolder {
        val view=LayoutInflater.from(parent.context)
        .inflate(R.layout.mappings_list_item, parent, false)

        return MappingViewHolder(view)
        }
    override fun onBindViewHolder(viewHolder: MappingViewHolder, position: Int) {
        viewHolder.bind(mappingsList[position])
        }
    override fun getItemCount() = mappingsList.size

    fun refresh() {
        mappingsList=providersManager.getAllModelProviderMappings()

        if (!filter.isEmpty()) {
            val filterProcessed=filter.lowercase()

            mappingsList=mappingsList
            .filter({ item ->
                item.first.lowercase().contains(filterProcessed)
                || (item.second?.name?.lowercase() ?: "none").contains(filterProcessed)
                })
            }

        notifyDataSetChanged()
        }
    fun setFilter(filter: String) {
        this.filter=filter
        refresh()
        }
    fun setItemClickListener(listener: ((Mapping) -> Unit)?) {
        itemClickListener=listener
        }
    fun onItemClick(item: Mapping) {
        itemClickListener?.invoke(item)
        }

    }
