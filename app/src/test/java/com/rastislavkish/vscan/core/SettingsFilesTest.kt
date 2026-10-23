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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import com.rastislavkish.vscan.core.files.LocalFileTree
import com.rastislavkish.vscan.core.files.VScanFiles

// The providers, secrets and settings files
@RunWith(RobolectricTestRunner::class)
class SettingsFilesTest {

    @get:Rule
    val temporary=TemporaryFolder()

    private lateinit var preferences: SharedPreferences
    private lateinit var files: VScanFiles
    private val problems=mutableListOf<String>()

    @Before
    fun setUp() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        preferences=context.getSharedPreferences("SettingsFilesTest", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        files=newFiles()
        }

    private fun newFiles() = VScanFiles(LocalFileTree(temporary.root.resolve("VScan")), temporary.root.resolve("manifest.json")).also {
        it.onProblem={ problem -> problems.add(problem) }
        }

    private fun providersManager() = ProvidersManager(preferences, newFiles()).also { it.load() }
    private fun settings() = Settings(preferences, newFiles()).also { it.load() }

    @Test
    fun apiKeysAreKeptInTheSecrets() {
        val manager=providersManager()
        val provider=manager.addProvider(Provider(name="OpenAI", baseUrl="https://api.openai.com/v1", apiKey="sk-secret", models=mapOf("custom" to "gpt-x")))
        manager.setDefaultProvider(provider.id)
        manager.mapModelToProvider("vscan-gpt-4o", null)

        val providers=files.readText("providers.toml")!!
        val secrets=files.readText("secrets.toml")!!
        assertFalse(providers, providers.contains("sk-secret"))
        assertTrue(providers, providers.contains("base_url = \"https://api.openai.com/v1\""))
        assertTrue(secrets, secrets.contains("api_key = \"sk-secret\""))

        val loaded=providersManager()
        assertEquals(provider, loaded.getProvider(provider.id))
        assertEquals(provider, loaded.getDefaultProvider())
        assertNull(loaded.getProviderForModel("vscan-gpt-4o"))
        assertEquals(provider, loaded.getProviderForModel("vscan-other"))
        }

    @Test
    fun brokenProvidersAreNotOverwritten() {
        files.write("providers.toml", "default_provider = ".toByteArray())

        val manager=providersManager()
        assertEquals(1, problems.size)
        manager.addProvider(Provider(name="A", baseUrl="https://a", apiKey="", models=mapOf()))

        assertEquals("default_provider = ", files.readText("providers.toml"))
        assertEquals(2, problems.size)
        }

    @Test
    fun settingsAreWrittenReadably() {
        val settings=settings()
        settings.useSounds=false
        settings.reportEveryScan=false
        settings.volumeUpPressAction=RunCommandAction("fd keys")
        settings.shakeAction=ScanWithConfigAction("find")
        settings.save()

        val text=files.readText("settings.toml")!!
        assertTrue(text, text.contains("use_sounds = false"))
        assertTrue(text, text.contains("[volume_up_press_action]\ntype = \"run_command\"\ncommand = \"fd keys\""))

        val loaded=settings()
        assertFalse(loaded.useSounds)
        assertFalse(loaded.reportEveryScan)
        assertEquals("fd keys", (loaded.volumeUpPressAction as RunCommandAction).command)
        assertEquals("find", (loaded.shakeAction as ScanWithConfigAction).config)
        assertNull(loaded.volumeDownPressAction)
        }

    @Test
    fun settingsMoveFromThePreferences() {
        preferences.edit()
        .putBoolean("useSounds", false)
        .putString("defaultConfig", "find")
        .putString("shakeAction", """{"type":"com.rastislavkish.vscan.core.StopSpeechAction"}""")
        .commit()

        val settings=settings()
        assertFalse(settings.useSounds)
        assertEquals("find", settings.defaultConfigId)
        assertTrue(settings.shakeAction is StopSpeechAction)
        assertTrue(files.readText("settings.toml")!!.contains("type = \"stop_speech\""))

        // Once
        preferences.edit().putBoolean("useSounds", true).commit()
        assertFalse(settings().useSounds)
        }

    @Test
    fun handEditedSettingsKeepTheRest() {
        files.write("settings.toml", "use_front_camera = true\n".toByteArray())

        val settings=settings()
        assertTrue(settings.useFrontCamera)
        assertTrue(settings.useSounds)
        assertEquals(ConfigManager.BASE_CONFIG_ID, settings.defaultConfigId)
        }
    }
