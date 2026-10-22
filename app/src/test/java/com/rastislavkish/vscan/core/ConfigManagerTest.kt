/*
* Copyright (C) 2026 Rastislav Kish
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
package com.rastislavkish.vscan.core

import android.content.Context
import android.content.SharedPreferences

import androidx.test.core.app.ApplicationProvider

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigManagerTest {

    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        preferences=context.getSharedPreferences("ConfigManagerTest", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        }

    private fun freshManager(): ConfigManager {
        val manager=ConfigManager(preferences)
        manager.load()
        return manager
        }

    @Test
    fun startsWithBuiltInConfigs() {
        val ids=freshManager().getList().map { it.id }

        assertEquals(listOf("base", "file-description"), ids)
        }

    @Test
    fun addConfigDerivesIdFromNameAndPersists() {
        val manager=freshManager()

        val config=manager.addConfig(Config().withName("Bus lines"))

        assertEquals("bus-lines", config.id)
        assertEquals("Bus lines", freshManager().getConfig("bus-lines")?.name)
        }

    @Test
    fun addConfigAvoidsTakenIds() {
        val manager=freshManager()

        val first=manager.addConfig(Config().withName("Bus lines"))
        val second=manager.addConfig(first)
        // Deriving a config from the base one keeps its name
        val derived=manager.addConfig(Config())

        assertEquals("bus-lines-2", second.id)
        assertEquals("base-2", derived.id)
        }

    @Test
    fun renamingKeepsId() {
        val manager=freshManager()
        val config=manager.addConfig(Config().withName("Before"))

        manager.updateConfig(config.withName("After"))

        assertEquals("After", freshManager().getConfig("before")?.name)
        }

    @Test
    fun deleteConfigPersists() {
        val manager=freshManager()
        val config=manager.addConfig(Config().withName("Doomed"))

        manager.deleteConfig(config)

        assertNull(manager.getConfig(config.id))
        assertNull(freshManager().getConfig(config.id))
        }

    @Test
    fun loadRestoresEditedBuiltInConfigs() {
        val manager=freshManager()
        manager.updateConfig(manager.getBaseConfig().withUserPrompt("Tampered"))

        assertEquals(Config().userPrompt, freshManager().getConfig("base")?.userPrompt)
        }

    @Test
    fun loadMigratesLegacyModelNames() {
        val legacy="""[{"id":"a","name":"A","model":"GPT_4O"},{"id":"b","name":"B","model":"GPT_4O_MINI"}]"""
        preferences.edit().putString("configurations", legacy).commit()

        val manager=freshManager()
        assertEquals("vscan-gpt-4o", manager.getConfig("a")?.model)
        assertEquals("vscan-gpt-4o-mini", manager.getConfig("b")?.model)
        }

    @Test
    fun loadIgnoresRemovedFields() {
        val legacy="""[{"id":"a","name":"A","highRes":true,"camera":"FRONT_CAMERA"}]"""
        preferences.edit().putString("configurations", legacy).commit()

        val config=freshManager().getConfig("a")

        assertEquals("A", config?.name)
        assertEquals(UsedCamera.FRONT_CAMERA, config?.camera)
        }

    }
