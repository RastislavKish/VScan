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

package com.rastislavkish.vscan.ui.provideractivity

import android.content.Context

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Model

class ModelsListAdapter(modelList: List<ModelToIdMapping>): RecyclerView.Adapter<ModelsListAdapter.ModelToIdMappingViewHolder>() {

    private var originalModelList=modelList
    private var modelList=modelList
    private var filter=""
    private var itemClickListener: ((ModelToIdMapping) -> Unit)?=null

    class ModelToIdMappingViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val itemTextView: TextView=view.findViewById(R.id.itemTextView)

        init {
            itemTextView.setOnClickListener(this::itemTextView_click)
            }

        private var mapping: ModelToIdMapping?=null

        fun bind(mapping: ModelToIdMapping) {
            this.mapping=mapping

            itemTextView.text=mapping.toPrettyString()
            }

        fun itemTextView_click(view: View) {
            (bindingAdapter as ModelsListAdapter?)?.onItemClick(mapping!!)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelToIdMappingViewHolder {
        val view=LayoutInflater.from(parent.context)
        .inflate(R.layout.provider_activity_models_list_item, parent, false)

        return ModelToIdMappingViewHolder(view)
        }
    override fun onBindViewHolder(viewHolder: ModelToIdMappingViewHolder, position: Int) {
        viewHolder.bind(modelList[position])
        }
    override fun getItemCount() = modelList.size

    fun refresh(modelList: List<ModelToIdMapping>) {
        originalModelList=modelList
        this.modelList=modelList

        if (!filter.isEmpty()) {
            val filterProcessed=filter.lowercase()

            this.modelList=modelList
            .filter({ item -> item.toPrettyString().lowercase().contains(filterProcessed) })
            }

        notifyDataSetChanged()
        }
    fun setFilter(filter: String) {
        this.filter=filter
        refresh(originalModelList)
        }
    fun setItemClickListener(listener: ((ModelToIdMapping) -> Unit)?) {
        itemClickListener=listener
        }
    fun onItemClick(item: ModelToIdMapping) {
        itemClickListener?.invoke(item)
        }

    }
