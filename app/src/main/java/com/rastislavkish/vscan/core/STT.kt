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

package com.rastislavkish.vscan.core

import android.os.Bundle

import android.content.Context
import android.content.Intent

import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.coroutines.channels.Channel

class STT(context: Context): RecognitionListener, CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private var job=Job()

    private val speechRecognizer=SpeechRecognizer.createSpeechRecognizer(context)

    init {
        speechRecognizer.setRecognitionListener(this)
        }

    private val resultChannel=Channel<String?>()
    private var recognitionMutex=Mutex()

    private val context=context

    suspend fun recognize(): String? {
        recognitionMutex.withLock {
            val recognizerIntent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            speechRecognizer.startListening(recognizerIntent)

            return resultChannel.receive()
            }
        }

    override fun onReadyForSpeech(params: Bundle) {

        }
    override fun onResults(params: Bundle) {
        val results: List<String> = params.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.toList() ?: listOf()

        if (!results.isEmpty()) {
            val result=results[0]

            launch {
                resultChannel.send(result)
                }
            }
        else {
            launch {
                resultChannel.send(null)
                }
            }
        }
    override fun onError(error: Int) {
        //if (error==SpeechRecognizer.ERROR_NO_MATCH)
        launch {
            resultChannel.send(null)
            }
        }

    override fun onBeginningOfSpeech() {}
    override fun onBufferReceived(buffer: ByteArray) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle) {}
    override fun onPartialResults(partialResults: Bundle) {}
    override fun onRmsChanged(rmsdB: Float) {}
    }
