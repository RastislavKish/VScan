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

package com.rastislavkish.vscan.ui.providersactivity

import android.content.Context

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Provider
import com.rastislavkish.vscan.core.ProvidersManager

class ProviderListAdapter(context: Context): RecyclerView.Adapter<ProviderListAdapter.ProviderViewHolder>() {

    private var providersManager=ProvidersManager.getInstance(context)
    private var providerList=providersManager.getAllProviders()
    private var itemClickListener: ((Provider) -> Unit)?=null

    class ProviderViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val itemTextView: TextView=view.findViewById(R.id.itemTextView)

        init {
            itemTextView.setOnClickListener(this::itemTextView_click)
            }

        private var provider: Provider?=null

        fun bind(provider: Provider) {
            this.provider=provider

            itemTextView.text=provider.name
            }

        fun itemTextView_click(view: View) {
            (bindingAdapter as ProviderListAdapter?)?.onItemClick(provider!!)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProviderViewHolder {
        val view=LayoutInflater.from(parent.context)
        .inflate(R.layout.provider_list_item, parent, false)

        return ProviderViewHolder(view)
        }
    override fun onBindViewHolder(viewHolder: ProviderViewHolder, position: Int) {
        viewHolder.bind(providerList[position])
        }
    override fun getItemCount() = providerList.size

    fun refresh() {
        providerList=providersManager.getAllProviders()

        notifyDataSetChanged()
        }
    fun setItemClickListener(listener: ((Provider) -> Unit)?) {
        itemClickListener=listener
        }
    fun onItemClick(item: Provider) {
        itemClickListener?.invoke(item)
        }

    }
