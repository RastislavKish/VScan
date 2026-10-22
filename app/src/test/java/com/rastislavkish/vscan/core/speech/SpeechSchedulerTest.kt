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
package com.rastislavkish.vscan.core.speech

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.speech.SpeechPriority.*

class SpeechSchedulerTest {

    private class FakeEngine: SpeechEngine {

        val spoken=mutableListOf<String>()
        var current: Pair<String, String>?=null
        var lastId: String?=null

        override fun speak(text: String, utteranceId: String) {
            spoken.add(text)
            current=Pair(text, utteranceId)
            lastId=utteranceId
            }
        override fun stop() {
            current=null
            }
        }

    private val engine=FakeEngine()
    private val activeChanges=mutableListOf<Boolean>()
    private val scheduler=SpeechScheduler(engine) { activeChanges.add(it) }

    // Lets the engine finish the current chunk
    private fun finish() {
        val id=engine.current!!.second
        engine.current=null
        scheduler.onUtteranceDone(id)
        }
    private fun finishAll() {
        while (engine.current!=null)
        finish()
        }
    private fun current(): String? = engine.current?.first

    @Test
    fun normalMessagesAreSpokenSentenceBySentenceInOrder() {
        scheduler.say("One. Two.", NORMAL)
        scheduler.say("Three.", NORMAL)

        finishAll()

        assertEquals(listOf("One.", "Two.", "Three."), engine.spoken)
        assertFalse(scheduler.isActive)
        }

    @Test
    fun highInterruptsNormalWhichResumesFromTheInterruptedSentence() {
        scheduler.say("One. Two. Three.", NORMAL)
        finish()
        assertEquals("Two.", current())

        scheduler.say("Scooter!", HIGH)
        assertEquals("Scooter!", current())

        finishAll()

        assertEquals(listOf("One.", "Two.", "Scooter!", "Two.", "Three."), engine.spoken)
        }

    @Test
    fun highReplacesHigh() {
        scheduler.say("First warning. More.", HIGH)
        scheduler.say("Second warning.", HIGH)

        finishAll()

        assertEquals(listOf("First warning.", "Second warning."), engine.spoken)
        }

    @Test
    fun criticalPausesHighWhichResumes() {
        scheduler.say("High one. High two.", HIGH)
        finish()

        scheduler.say("Critical.", CRITICAL)
        assertEquals("Critical.", current())

        finishAll()

        assertEquals(listOf("High one.", "High two.", "Critical.", "High two."), engine.spoken)
        }

    @Test
    fun criticalMessagesQueueBehindEachOther() {
        scheduler.say("First critical.", CRITICAL)
        scheduler.say("Second critical.", CRITICAL)
        scheduler.say("High.", HIGH)

        assertEquals("First critical.", current())
        finishAll()

        assertEquals(listOf("First critical.", "Second critical.", "High."), engine.spoken)
        }

    @Test
    fun highWaitsWhileCriticalSpeaks() {
        scheduler.say("Critical one. Critical two.", CRITICAL)
        scheduler.say("High.", HIGH)

        assertEquals("Critical one.", current())
        finishAll()

        assertEquals(listOf("Critical one.", "Critical two.", "High."), engine.spoken)
        }

    @Test
    fun lowIsDroppedWhenHighOrCriticalAppears() {
        scheduler.say("Low speaking. Low more.", LOW)
        scheduler.say("Low queued.", LOW)
        scheduler.say("Normal queued.", NORMAL)

        scheduler.say("High.", HIGH)
        finishAll()

        assertEquals(listOf("Low speaking.", "High.", "Normal queued."), engine.spoken)
        }

    @Test
    fun lowIsNotDroppedByNormal() {
        scheduler.say("Low.", LOW)
        scheduler.say("Normal.", NORMAL)

        finishAll()

        assertEquals(listOf("Low.", "Normal."), engine.spoken)
        }

    @Test
    fun normalWaitsBehindSpeakingLow() {
        scheduler.say("Low one. Low two.", LOW)
        scheduler.say("Normal.", NORMAL)

        assertEquals("Low one.", current())
        finishAll()

        assertEquals(listOf("Low one.", "Low two.", "Normal."), engine.spoken)
        }

    @Test
    fun stopClearsEverythingButCritical() {
        scheduler.say("Critical one. Critical two.", CRITICAL)
        scheduler.say("High.", HIGH)
        scheduler.say("Normal.", NORMAL)

        scheduler.stop()
        assertEquals("Critical one.", current())

        finishAll()

        assertEquals(listOf("Critical one.", "Critical two."), engine.spoken)
        }

    @Test
    fun stopSilencesNormalSpeech() {
        scheduler.say("One. Two.", NORMAL)
        scheduler.say("Three.", LOW)

        scheduler.stop()

        assertNull(current())
        assertFalse(scheduler.isActive)
        assertEquals(listOf("One."), engine.spoken)
        }

    @Test
    fun lateCallbacksOfInterruptedUtterancesAreIgnored() {
        scheduler.say("Normal one. Normal two.", NORMAL)
        val interruptedId=engine.lastId!!

        scheduler.say("High.", HIGH)
        // The engine reports the interrupted utterance only after the new one started
        scheduler.onUtteranceDone(interruptedId)

        assertEquals("High.", current())
        finishAll()

        assertEquals(listOf("Normal one.", "High.", "Normal one.", "Normal two."), engine.spoken)
        }

    @Test
    fun reportsActivityChanges() {
        scheduler.say("One.", NORMAL)
        scheduler.say("Two.", HIGH)
        finishAll()
        scheduler.say("Three.", NORMAL)
        scheduler.stop()

        assertEquals(listOf(true, false, true, false), activeChanges)
        }

    @Test
    fun emptyMessagesAreIgnored() {
        scheduler.say("  \n ", HIGH)

        assertNull(current())
        assertTrue(activeChanges.isEmpty())
        }

    }
