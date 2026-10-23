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

import com.rastislavkish.vscan.core.config.*
import com.rastislavkish.vscan.core.imagesource.Zoom
import com.rastislavkish.vscan.core.speech.SpeechPriority
import com.rastislavkish.vscan.core.template.Template

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

        val config=manager.addConfig(Config(name="Bus lines"))

        assertEquals("bus-lines", config.id)
        assertEquals("Bus lines", freshManager().getConfig("bus-lines")?.name)
        }

    @Test
    fun addConfigAvoidsTakenIds() {
        val manager=freshManager()

        val first=manager.addConfig(Config(name="Bus lines"))
        val second=manager.addConfig(first)
        // Deriving a config from the base one keeps its name
        val derived=manager.addConfig(Config())

        assertEquals("bus-lines-2", second.id)
        assertEquals("base-2", derived.id)
        }

    @Test
    fun renamingKeepsId() {
        val manager=freshManager()
        val config=manager.addConfig(Config(name="Before"))

        manager.updateConfig(config.copy(name="After"))

        assertEquals("After", freshManager().getConfig("before")?.name)
        }

    @Test
    fun deleteConfigPersists() {
        val manager=freshManager()
        val config=manager.addConfig(Config(name="Doomed"))

        manager.deleteConfig(config)

        assertNull(manager.getConfig(config.id))
        assertNull(freshManager().getConfig(config.id))
        }

    @Test
    fun loadRestoresEditedBuiltInConfigs() {
        val manager=freshManager()
        manager.updateConfig(manager.getBaseConfig().withLlm { it.copy(userPrompt="Tampered") })

        assertEquals(Config().llm.userPrompt, freshManager().getConfig("base")?.llm?.userPrompt)
        }

    @Test
    fun loadMigratesLegacyModelNames() {
        val legacy="""[{"id":"a","name":"A","model":"GPT_4O"},{"id":"b","name":"B","model":"GPT_4O_MINI"}]"""
        preferences.edit().putString("configurations", legacy).commit()

        val manager=freshManager()
        assertEquals("vscan-gpt-4o", manager.getConfig("a")?.llm?.model)
        assertEquals("vscan-gpt-4o-mini", manager.getConfig("b")?.llm?.model)
        }

    @Test
    fun loadIgnoresRemovedFields() {
        val legacy="""[{"id":"a","name":"A","highRes":true,"camera":"FRONT_CAMERA","model":"vscan-x"}]"""
        preferences.edit().putString("configurations", legacy).commit()

        val config=freshManager().getConfig("a")

        assertEquals("A", config?.name)
        assertEquals("vscan-x", config?.llm?.model)
        }

        @Test
    fun migratesV1Configs() {
        val legacy="""[{"id":"a","name":"A","systemPrompt":"Be brief","userPrompt":"Read {{ it }}","flashlightMode":"ON","maxCompletionTokens":500,"reasoningEffort":"LOW"},{"id":"b","name":"B"}]"""
        preferences.edit().putString("configurations", legacy).commit()

        val a=freshManager().getConfig("a")!!
        assertEquals("Be brief", a.llm.systemPrompt)
        assertEquals(Template.escape("Read {{ it }}"), a.llm.userPrompt)
        assertEquals(500, a.llm.maxCompletionTokens)
        assertEquals(ReasoningEffort.LOW, a.llm.reasoningEffort)
        assertEquals(FlashlightMode.ON, a.lifetime.flashlight)
        assertEquals(LlmChangeDetectionMode.APP_DRIVEN, a.llm.changeDetection.mode)
        assertEquals(listOf(ToastOperation()), a.operations)
        assertFalse(a.lifetime.isLooping)

        // The old defaults, which the old versions didn't store
        val b=freshManager().getConfig("b")!!
        assertEquals("What's in the image?", b.llm.userPrompt)
        assertEquals("vscan-gpt-4o", b.llm.model)
        assertEquals(300, b.llm.maxCompletionTokens)
        assertNull(b.llm.reasoningEffort)
        assertEquals(FlashlightMode.DEFAULT, b.lifetime.flashlight)
        }

    @Test
    fun migrationKeepsV1ConfigsAndRunsOnce() {
        val legacy="""[{"id":"a","name":"A"}]"""
        preferences.edit().putString("configurations", legacy).commit()
        freshManager()

        assertEquals(legacy, preferences.getString("configurations", null))

        // Later changes to the backup don't matter anymore
        preferences.edit().putString("configurations", """[{"id":"c","name":"C"}]""").commit()
        val manager=freshManager()
        assertNotNull(manager.getConfig("a"))
        assertNull(manager.getConfig("c"))
        }

    @Test
    fun persistsAllConfigParts() {
        val config=Config(
            name="Bus line reader",
            command="blr",
            description="Reports the line numbers of buses",
            parameters=listOf(
                Parameter(Parameter.IMAGE_PARAMETER_NAME, ParameterType.IMAGE_SOURCE, short="i", default="\$glasses"),
                Parameter("object", ParameterType.STRING, positional=true, rest=true, default="keys"),
                ),
            lens=ConfigLens(listOf(Zoom(2f, centerY=0.4f)), LensTarget.ALL),
            analyzer=LlmAnalyzer(
                userPrompt="Read the line of the bus. {% if lastOutput %}Last time: {{ lastOutput }}{% endif %}",
                appendImage=false,
                changeDetection=LlmChangeDetection(LlmChangeDetectionMode.LLM_DRIVEN, "CHANGE: *", ChangeMarkerMeaning.CHANGE, MarkerMatch.WILDCARD),
                ),
            operations=listOf(
                SayOperation("changed", "Bus {{ lastOutput }}", SpeechPriority.HIGH),
                PlayOperation(sound="beep", repeat=0, volume=NumberValue(value=0.8), pan=NumberValue(extract=Extraction.JSON, path="x"), instance=SoundInstance.NEW, stopOthers=true),
                VibrateOperation(pattern=listOf(0, 100, 50, 100), repeat=2),
                ToastOperation(text="{{ rawOutput }}"),
                ),
            lifetime=Lifetime(interval=2.0, capture=CaptureMode.PHOTO, iterationCondition="iteration<10", maxIterations=5, maxDuration=60.0, maxTokens=1000),
            )

        val added=freshManager().addConfig(config)

        assertEquals(added, freshManager().getConfig(added.id))
        }

    @Test
    fun storesDefaultValues() {
        freshManager().addConfig(Config(name="Derived"))

        // So changing the base config's definition doesn't change configs derived from it
        val stored=preferences.getString("configs", null)!!
        assertTrue(stored.contains("\"userPrompt\":\"What's in the image?\""))
        assertTrue(stored.contains("\"type\":\"toast\""))
        }

    @Test
    fun fileDescriptionConfigReportsNothing() {
        assertEquals(listOf<Operation>(), freshManager().getFileDescriptionConfig().operations)
        }

    @Test
    fun imageParameterIsImplicit() {
        assertEquals(listOf(Parameter.defaultImageParameter), Config().allParameters)

        val custom=Parameter(Parameter.IMAGE_PARAMETER_NAME, ParameterType.IMAGE_SOURCE, default="\$glasses")
        assertEquals(listOf(custom), Config(parameters=listOf(custom)).allParameters)
        }

}
