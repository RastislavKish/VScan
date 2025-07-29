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

package com.rastislavkish.vscan.ui.providerpresetselectionactivity

import android.content.Context

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.rastislavkish.vscan.R

class ProviderParamsListAdapter(context: Context): RecyclerView.Adapter<ProviderParamsListAdapter.ProviderParamsViewHolder>() {

    private var providerParamsList=ProviderParams.presets
    private var filter=""
    private var itemClickListener: ((ProviderParams) -> Unit)?=null

    class ProviderParamsViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val itemTextView: TextView=view.findViewById(R.id.itemTextView)

        init {
            itemTextView.setOnClickListener(this::itemTextView_click)
            }

        private var providerParams: ProviderParams?=null

        fun bind(providerParams: ProviderParams) {
            this.providerParams=providerParams

            itemTextView.text=providerParams.name
            }

        fun itemTextView_click(view: View) {
            (bindingAdapter as ProviderParamsListAdapter?)?.onItemClick(providerParams!!)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProviderParamsViewHolder {
        val view=LayoutInflater.from(parent.context)
        .inflate(R.layout.provider_params_list_item, parent, false)

        return ProviderParamsViewHolder(view)
        }
    override fun onBindViewHolder(viewHolder: ProviderParamsViewHolder, position: Int) {
        viewHolder.bind(providerParamsList[position])
        }
    override fun getItemCount() = providerParamsList.size

    fun refresh() {
        providerParamsList=ProviderParams.presets

        if (!filter.isEmpty()) {
            val filterProcessed=filter.lowercase()

            providerParamsList=providerParamsList
            .filter({ item -> item.name.lowercase().contains(filterProcessed) })
            }

        notifyDataSetChanged()
        }
    fun setFilter(filter: String) {
        this.filter=filter
        refresh()
        }
    fun setItemClickListener(listener: ((ProviderParams) -> Unit)?) {
        itemClickListener=listener
        }
    fun onItemClick(item: ProviderParams) {
        itemClickListener?.invoke(item)
        }

    }
