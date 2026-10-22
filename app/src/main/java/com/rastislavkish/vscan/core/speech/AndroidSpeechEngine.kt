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

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log

import com.rastislavkish.vscan.core.speech.SpeechSystem.Companion.TAG

// The system text to speech as a SpeechEngine
// All callbacks are delivered on the main thread
class AndroidSpeechEngine(
    context: Context,
    private val useAccessibilityVolume: () -> Boolean,
    private val onUtteranceDone: (String) -> Unit,
    private val onUnavailable: () -> Unit,
    ): SpeechEngine {

    private val handler=Handler(Looper.getMainLooper())

    private var ready=false
    private var failed=false
    // The utterance requested before the engine finished initializing
    private var pending: Pair<String, String>?=null

    private val tts=TextToSpeech(context.applicationContext) { status ->
        handler.post { onInit(status) }
        }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

            override fun onStart(utteranceId: String) {
                Log.d(TAG, "Started $utteranceId")
                }

            override fun onDone(utteranceId: String) {
                Log.d(TAG, "Done $utteranceId")
                handler.post { onUtteranceDone(utteranceId) }
                }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String) {
                Log.d(TAG, "Error $utteranceId")
                handler.post { onUtteranceDone(utteranceId) }
                }
            override fun onError(utteranceId: String, errorCode: Int) {
                Log.d(TAG, "Error $utteranceId, code $errorCode")
                handler.post { onUtteranceDone(utteranceId) }
                }

            // Besides the stops the scheduler requests, whose late reports it ignores, the engine may stop an utterance on its own, e.g. for other speech like TalkBack's
            // Such an utterance never reports being done, so it must end here, or the scheduler would wait for it forever
            override fun onStop(utteranceId: String, interrupted: Boolean) {
                Log.d(TAG, "Stopped $utteranceId")
                handler.post { onUtteranceDone(utteranceId) }
                }
            })
        }

    override fun speak(text: String, utteranceId: String) {
        when {
            failed -> handler.post { onUtteranceDone(utteranceId) }
            !ready -> pending=Pair(text, utteranceId)
            else -> startSpeaking(text, utteranceId)
            }
        }

    override fun stop() {
        Log.d(TAG, "Stopping")
        pending=null

        if (ready)
        tts.stop()
        }

    private fun onInit(status: Int) {
        val pending=pending
        this.pending=null

        if (status==TextToSpeech.SUCCESS) {
            ready=true

            if (pending!=null)
            startSpeaking(pending.first, pending.second)
            }
        else {
            failed=true
            onUnavailable()

            if (pending!=null)
            onUtteranceDone(pending.second)
            }
        }

    private fun startSpeaking(text: String, utteranceId: String) {
        val usage=if (useAccessibilityVolume())
        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
        else
        AudioAttributes.USAGE_MEDIA

        tts.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())

        Log.d(TAG, "Speaking $utteranceId: $text")

        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)==TextToSpeech.ERROR) {
            Log.d(TAG, "Speaking $utteranceId failed")
            handler.post { onUtteranceDone(utteranceId) }
            }
        }
    }
