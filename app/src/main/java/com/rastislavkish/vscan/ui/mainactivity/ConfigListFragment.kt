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

import java.util.Base64

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.NavHostFragment

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.TextController
import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.LocalImage
import com.rastislavkish.vscan.core.openai.ImageMessage

class ConfigListFragment: Fragment(), CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private lateinit var job: Job

    private lateinit var settings: Settings
    private lateinit var tabAdapter: TabAdapter
    private lateinit var configSorter: ConfigSorter
    private lateinit var configListAdapter: ConfigListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
        ): View? {
        return inflater.inflate(R.layout.fragment_config_list, container, false)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        job=Job()
        settings=Settings.getInstance(context!!)
        tabAdapter=TabAdapter.getInstance(context!!)
        configSorter=ConfigSorter.getInstance(context!!)
        configListAdapter=ConfigListAdapter(context!!, configSorter)
        configListAdapter.setItemClickListener(this::configClick)
        configListAdapter.setItemLongClickListener(this::configLongClick)
        val configList: RecyclerView=view.findViewById(R.id.configList)
        configList.adapter=configListAdapter

        val searchInput: EditText=view.findViewById(R.id.searchInput)
        TextController(searchInput).setTextChangeListener(this::searchInputTextChange)
        }

    override fun onResume() {
        configListAdapter.refresh()
        super.onResume()
        }

    fun configClick(config: Config) {
        launch { tabAdapter.mutex.withLock {
            tabAdapter.activeConfig=config
            configSorter.markSelection(config.id)

            val navHostFragment = activity!!.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController
            navController.navigateUp()
            }}
        }
    fun configLongClick(config: Config) {
        launch { tabAdapter.mutex.withLock {
            toast(tabAdapter.consultConfig(config) ?: return@launch)
            }}
        }
    fun searchInputTextChange(text: String) {
        configListAdapter.setFilter(text)
        }

    fun toast(text: String) {
        Toast.makeText(activity!!, text, Toast.LENGTH_LONG).show()
        }
    }
