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

package com.rastislavkish.vscan.ui.modelselectionactivity

import android.content.Context

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Model
import com.rastislavkish.vscan.core.ProvidersManager

class ModelListAdapter(context: Context): RecyclerView.Adapter<ModelListAdapter.ModelViewHolder>() {

    private val context=context

    private var modelList=Model.presets
    private var displayedModels=DisplayedModels.ALL
    private var filter=""
    private var itemClickListener: ((Model) -> Unit)?=null

    class ModelViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val itemTextView: TextView=view.findViewById(R.id.itemTextView)

        init {
            itemTextView.setOnClickListener(this::itemTextView_click)
            }

        private var model: Model?=null

        fun bind(model: Model) {
            this.model=model

            itemTextView.text=model.name
            }

        fun itemTextView_click(view: View) {
            (bindingAdapter as ModelListAdapter?)?.onItemClick(model!!)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view=LayoutInflater.from(parent.context)
        .inflate(R.layout.model_list_item, parent, false)

        return ModelViewHolder(view)
        }
    override fun onBindViewHolder(viewHolder: ModelViewHolder, position: Int) {
        viewHolder.bind(modelList[position])
        }
    override fun getItemCount() = modelList.size

    fun refresh() {
        modelList=when (displayedModels) {
            DisplayedModels.ALL -> Model.presets
            DisplayedModels.READY_TO_USE -> readyToUseModels()
            DisplayedModels.SUPPORTED_BY_PROVIDERS -> supportedByProvidersModels()
            }

        if (!filter.isEmpty()) {
            val filterProcessed=filter.lowercase()

            modelList=modelList
            .filter({ item -> item.name.lowercase().contains(filterProcessed) })
            }

        notifyDataSetChanged()
        }
    fun setFilter(filter: String) {
        this.filter=filter
        refresh()
        }
    fun setDisplayedModels(displayedModels: DisplayedModels) {
        this.displayedModels=displayedModels
        }
    fun setItemClickListener(listener: ((Model) -> Unit)?) {
        itemClickListener=listener
        }
    fun onItemClick(item: Model) {
        itemClickListener?.invoke(item)
        }

    private fun readyToUseModels(): List<Model> {
        val supportedModels=hashSetOf<String>()
        for (model in supportedByProvidersModels())
        supportedModels.add(model.identifier)

        val providersManager=ProvidersManager.getInstance(context)
        val mappings=providersManager.getAllModelProviderMappings()

        val models=mutableListOf<Model>()

        for (mapping in mappings) {
            val (model, provider)=mapping

            if (provider==null)
            continue

            if (model.startsWith("vscan-")) {
                if (supportedModels.contains(model))
                models.add(Model.idToModel(model))
                }
            else {
                models.add(Model.idToModel(model))
                }
            }

        return models
        }
    private fun supportedByProvidersModels(): List<Model> {
        val providersManager=ProvidersManager.getInstance(context)
        val providers=providersManager.getAllProviders()

        val models=mutableListOf<Model>()
        for (provider in providers)
        for (model in provider.models.keys)
        models.add(Model.idToModel(model))

        return models
        }

    }
