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

// Decides what the speech engine says according to message priorities
// Not thread-safe, all calls including the engine callbacks must come from a single thread
class SpeechScheduler(
    private val engine: SpeechEngine,
    private val onActiveChange: (Boolean) -> Unit={},
    ) {

    private class Utterance(
        val priority: SpeechPriority,
        val chunks: List<String>,
        ) {

        // Index of the chunk to speak next, an interrupted chunk is spoken again from its start
        var position=0
        }

    private val critical=ArrayDeque<Utterance>()
    private var high: Utterance?=null
    // Normal and low priority messages in their order of arrival
    private val queue=ArrayDeque<Utterance>()

    private var speaking: Utterance?=null
    private var speakingId: String?=null
    private var nextUtteranceId=0L

    val isActive: Boolean
    get() = speaking!=null

    fun say(text: String, priority: SpeechPriority) {
        val chunks=SentenceSplitter.split(text)
        if (chunks.isEmpty())
        return

        val utterance=Utterance(priority, chunks)

        when (priority) {
            SpeechPriority.CRITICAL -> {
                critical.addLast(utterance)
                dropLow()
                }
            SpeechPriority.HIGH -> {
                high=utterance
                dropLow()
                }
            SpeechPriority.NORMAL, SpeechPriority.LOW -> queue.addLast(utterance)
            }

        update()
        }

    // The user's request for silence, which critical messages ignore
    fun stop() {
        high=null
        queue.clear()
        update()
        }

    // To be called by the engine when an utterance has finished or failed
    fun onUtteranceDone(utteranceId: String) {
        // Ends of interrupted utterances arrive late and have no effect
        if (utteranceId!=speakingId)
        return

        val utterance=speaking!!
        speaking=null
        speakingId=null

        utterance.position++
        if (utterance.position>=utterance.chunks.size)
        remove(utterance)

        update(wasActive=true)
        }

    private fun dropLow() {
        queue.removeAll { it.priority==SpeechPriority.LOW }
        }
    private fun remove(utterance: Utterance) {
        critical.remove(utterance)
        if (high===utterance)
        high=null
        queue.remove(utterance)
        }

    private fun next(): Utterance? = critical.firstOrNull() ?: high ?: queue.firstOrNull()

    private fun update(wasActive: Boolean=isActive) {
        val next=next()

        if (next!==speaking) {
            if (speaking!=null) {
                speaking=null
                speakingId=null
                engine.stop()
                }

            if (next!=null) {
                val id="vscan-${nextUtteranceId++}"
                speaking=next
                speakingId=id
                engine.speak(next.chunks[next.position], id)
                }
            }

        if (isActive!=wasActive)
        onActiveChange(isActive)
        }
    }
