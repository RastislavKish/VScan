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

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import android.view.View

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Provider
import com.rastislavkish.vscan.core.ProvidersManager

import com.rastislavkish.vscan.ui.provideractivity.ProviderActivityInput

import com.rastislavkish.vscan.ui.providerselectionactivity.ProviderSelectionActivity
import com.rastislavkish.vscan.ui.providerselectionactivity.ProviderSelectionActivityOutput

class ProvidersActivity : AppCompatActivity() {

    private lateinit var providersManager: ProvidersManager

    private lateinit var defaultProviderSelector: TextView

    private lateinit var providerListAdapter: ProviderListAdapter

    private lateinit var providerSelectionActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_providers)

        providersManager=ProvidersManager.getInstance(this)

        defaultProviderSelector=findViewById(R.id.defaultProviderSelector)

        providerListAdapter=ProviderListAdapter(this)
        providerListAdapter.setItemClickListener(this::providerClick)
        val providerList: RecyclerView=findViewById(R.id.providerList)
        providerList.adapter=providerListAdapter

        providerSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::providerSelectionActivityResult)
        }

    override fun onResume() {
        providerListAdapter.refresh()
        refreshDefaultProviderSelector()
        super.onResume()
        }
    override fun onPause() {

        super.onPause()
        }

    fun providerClick(provider: Provider) {
        startProviderActivity(provider.id)
        }

    fun onDefaultProviderSelectorClick(v: View) {
        startProviderSelectionActivity()
        }
    fun onResetDefaultProviderButtonClick(v: View) {
        providersManager.setDefaultProvider(null)
        refreshDefaultProviderSelector()
        }

    fun onAddProviderButtonClick(v: View) {
        startProviderActivity()
        }

    fun refreshDefaultProviderSelector() {
        val defaultProviderName=providersManager.getDefaultProvider()?.name ?: "None"
        defaultProviderSelector.text="Default provider: $defaultProviderName"
        }

    fun startProviderActivity(provider: Int?=null) {
        val intent=ProviderActivityInput(provider)
        .toIntent(this)
        startActivity(intent)
        }
    fun startProviderSelectionActivity() {
        val intent=Intent(this, ProviderSelectionActivity::class.java)
        providerSelectionActivityLauncher.launch(intent)
        }
    fun providerSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ProviderSelectionActivityOutput.fromIntent(result.data, "ProvidersActivity")

            providersManager.setDefaultProvider(output.provider)
            refreshDefaultProviderSelector()
            }
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
