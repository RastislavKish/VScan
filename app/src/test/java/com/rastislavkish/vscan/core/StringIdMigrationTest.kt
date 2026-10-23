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

import com.rastislavkish.vscan.ui.mainactivity.ConfigSorter

@RunWith(RobolectricTestRunner::class)
class StringIdMigrationTest {

    private lateinit var configPreferences: SharedPreferences
    private lateinit var settingsPreferences: SharedPreferences
    private lateinit var providersPreferences: SharedPreferences
    private lateinit var sorterPreferences: SharedPreferences

    // The formats below are what versions up to 0.2.x wrote, including the omission of default values
    private val legacyConfigs="""[
        {"systemPrompt":"","userPrompt":"What's in the image?"},
        {"id":-3,"name":"File description","model":"vscan-gpt-4o-mini"},
        {"id":0,"name":"Bus lines","userPrompt":"Line?"},
        {"id":1,"name":"Bus lines"},
        {"id":2,"name":"Čítanie"}
        ]"""
    private val legacyProviders="""{
        "0":{"id":0,"name":"OpenAI","baseUrl":"https://api.openai.com/v1","apiKey":"a","models":{}},
        "1":{"id":1,"name":"OpenAI","baseUrl":"https://api.openai.com/v1","apiKey":"b","models":{"x":"y"}}
        }"""

    @Before
    fun setUp() {
        val context=ApplicationProvider.getApplicationContext<Context>()

        configPreferences=context.getSharedPreferences("configs", Context.MODE_PRIVATE)
        settingsPreferences=context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        providersPreferences=context.getSharedPreferences("providers", Context.MODE_PRIVATE)
        sorterPreferences=context.getSharedPreferences("sorter", Context.MODE_PRIVATE)

        for (preferences in listOf(configPreferences, settingsPreferences, providersPreferences, sorterPreferences))
        preferences.edit().clear().commit()
        }

    private fun writeLegacyData() {
        configPreferences.edit()
        .putString("configurations", legacyConfigs)
        .commit()

        settingsPreferences.edit()
        .putBoolean("useFlashlight", true)
        .putInt("defaultConfigId", 1)
        .putInt("shareConfigId", 7)
        .putInt("fileDescriptionConfigId", -3)
        .putString("shakeAction", """{"type":"com.rastislavkish.vscan.core.ScanWithConfigAction","config":2}""")
        .putString("volumeUpPressAction", """{"type":"com.rastislavkish.vscan.core.ConsultConfigAction","config":9}""")
        .putString("volumeDownPressAction", """{"type":"com.rastislavkish.vscan.core.AskAction"}""")
        .commit()

        providersPreferences.edit()
        .putString("providers", legacyProviders)
        .putInt("defaultProvider", 1)
        .putString("modelProviderMappings", """{"vscan-gpt-4o":1,"vscan-deleted":5,"vscan-none":null}""")
        .commit()

        sorterPreferences.edit()
        .putString("selectionData", """{"0":100,"2":300,"9":200}""")
        .commit()
        }

    private fun migrate() {
        StringIdMigration(configPreferences, settingsPreferences, providersPreferences, sorterPreferences).run()
        }

    private fun loadConfigManager(): ConfigManager {
        val manager=ConfigManager(configPreferences)
        manager.load()
        return manager
        }
    private fun loadSettings(): Settings {
        val settings=Settings(settingsPreferences)
        settings.load()
        return settings
        }
    private fun loadProvidersManager(): ProvidersManager {
        val manager=ProvidersManager(providersPreferences)
        manager.load()
        return manager
        }

    @Test
    fun configsGetSlugIdsAndKeepTheirContent() {
        writeLegacyData()
        migrate()

        val manager=loadConfigManager()

        assertEquals(listOf("base", "file-description", "bus-lines", "bus-lines-2", "citanie", "find"), manager.getList().map { it.id })
        assertEquals("Line?", manager.getConfig("bus-lines")?.llm?.userPrompt)
        assertEquals("Čítanie", manager.getConfig("citanie")?.name)
        }

    @Test
    fun settingsFollowTheConfigMapping() {
        writeLegacyData()
        migrate()

        val settings=loadSettings()

        assertTrue(settings.useFlashlight)
        assertEquals("bus-lines-2", settings.defaultConfigId)
        // Pointed to a deleted config, so falls back to the default just as before
        assertEquals("base", settings.shareConfigId)
        assertEquals("file-description", settings.fileDescriptionConfigId)
        }

    @Test
    fun actionsFollowTheConfigMapping() {
        writeLegacyData()
        migrate()

        val settings=loadSettings()

        assertEquals("citanie", (settings.shakeAction as ScanWithConfigAction).config)
        assertEquals("base", (settings.volumeUpPressAction as ConsultConfigAction).config)
        assertTrue(settings.volumeDownPressAction is AskAction)
        }

    @Test
    fun sorterFollowsTheConfigMapping() {
        writeLegacyData()
        migrate()

        val sorter=ConfigSorter(sorterPreferences)
        sorter.load()
        val sorted=sorter.sortConfigList(loadConfigManager().getList()).map { it.id }

        assertEquals(listOf("citanie", "bus-lines"), sorted.take(2))
        assertFalse(sorterPreferences.getString("selectionData", "")!!.contains("\"9\""))
        }

    @Test
    fun providersAndMappingsGetSlugIds() {
        writeLegacyData()
        migrate()

        val manager=loadProvidersManager()

        assertEquals(setOf("openai", "openai-2"), manager.getAllProviders().map { it.id }.toSet())
        assertEquals("b", manager.getProvider("openai-2")?.apiKey)
        assertEquals("openai-2", manager.getDefaultProvider()?.id)
        assertEquals("openai-2", manager.getProviderForModel("vscan-gpt-4o")?.id)
        // Explicitly mapped to no provider, including the one mapped to a deleted provider
        assertNull(manager.getProviderForModel("vscan-deleted"))
        assertNull(manager.getProviderForModel("vscan-none"))
        // Unmapped models still use the default provider
        assertEquals("openai-2", manager.getProviderForModel("vscan-other")?.id)
        }

    @Test
    fun legacyValuesAreBackedUp() {
        writeLegacyData()
        migrate()

        assertEquals(legacyConfigs, configPreferences.getString("configurations_v1", null))
        assertEquals(legacyProviders, providersPreferences.getString("providers_v1", null))
        assertEquals(1, settingsPreferences.getInt("defaultConfigId_v1", -1))
        assertEquals(1, providersPreferences.getInt("defaultProvider_v1", -1))
        assertFalse(settingsPreferences.contains("defaultConfigId"))
        assertFalse(providersPreferences.contains("defaultProvider"))
        }

    @Test
    fun rerunningChangesNothing() {
        writeLegacyData()
        migrate()

        val snapshot=listOf(configPreferences, settingsPreferences, providersPreferences, sorterPreferences).map { it.all.toMap() }
        migrate()

        assertEquals(snapshot, listOf(configPreferences, settingsPreferences, providersPreferences, sorterPreferences).map { it.all.toMap() })
        }

    @Test
    fun freshInstallIsLeftEmpty() {
        migrate()

        for (preferences in listOf(configPreferences, settingsPreferences, providersPreferences, sorterPreferences))
        assertTrue(preferences.all.isEmpty())
        }

    @Test
    fun dataWithoutExplicitIdsNeedsNoMigration() {
        // Only the base config, whose id is omitted in both formats
        val configs="""[{"userPrompt":"Hi"}]"""
        configPreferences.edit().putString("configurations", configs).commit()

        migrate()

        assertEquals(configs, configPreferences.getString("configurations", null))
        assertFalse(configPreferences.contains("configurations_v1"))
        }

    }
