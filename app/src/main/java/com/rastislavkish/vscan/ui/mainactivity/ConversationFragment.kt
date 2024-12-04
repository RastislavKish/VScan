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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment

import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.openai.TextMessage

class ConversationFragment: Fragment(), CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private lateinit var job: Job

    private lateinit var adapter: TabAdapter
    private val conversationListAdapter=ConversationListAdapter()

    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
        ): View? {
        return inflater.inflate(R.layout.fragment_conversation, container, false)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        job=Job()

        adapter=TabAdapter.getInstance(context!!)

        val conversationList: RecyclerView=view.findViewById(R.id.conversationList)
        conversationList.adapter=conversationListAdapter

        messageInput=view.findViewById(R.id.messageInput)
        messageInput.setOnEditorActionListener(this::onMessageInputEditorAction)
        sendButton=view.findViewById(R.id.sendButton)
        sendButton.setOnClickListener(this::sendButtonClick)
        }

    override fun onResume() {
        super.onResume()

        launch { adapter.mutex.withLock {
            conversationListAdapter.refresh(adapter.conversation)
            }}
        }

    fun sendButtonClick(v: View) {
        val message=messageInput.text.toString()
        messageInput.text.clear()
        sendButton.setClickable(false)
        launch { adapter.mutex.withLock {
            val userMessage=TextMessage(message)
            conversationListAdapter.addMessage(userMessage)
            adapter.conversation.addMessage(userMessage)
            adapter.conversation.generateResponse()
            val response=adapter.conversation.getLastMessage()

            if (response!=null) {
                conversationListAdapter.addMessage(response)
                }
            else {
                toast("Error: Unable to obtain response")
                }
            sendButton.setClickable(true)
            }}
        }

    fun onMessageInputEditorAction(v: View, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId==EditorInfo.IME_ACTION_DONE) {
            sendButton.performClick()
            return true
            }

        return false
        }

    fun toast(text: String) {
        Toast.makeText(activity!!, text, Toast.LENGTH_LONG).show()
        }
    }
