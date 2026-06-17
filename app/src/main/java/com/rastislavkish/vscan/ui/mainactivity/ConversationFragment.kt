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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment

import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.google.android.material.textfield.TextInputEditText
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.openai.Message
import com.rastislavkish.vscan.core.openai.TextMessage
import com.rastislavkish.vscan.core.openai.SystemMessage
import com.rastislavkish.vscan.core.openai.ImageMessage
import com.rastislavkish.vscan.core.openai.AssistantMessage
import com.rastislavkish.vscan.core.openai.Image

import com.rastislavkish.vscan.ui.textinputactivity.TextInputActivityInput
import com.rastislavkish.vscan.ui.textinputactivity.TextInputActivityOutput

class ConversationFragment: Fragment(), CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private lateinit var job: Job

    private lateinit var adapter: TabAdapter
    private lateinit var conversationListAdapter: ConversationListAdapter

    private lateinit var usageHeader: TextView
    private lateinit var conversationList: RecyclerView
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: Button

    private lateinit var textInputActivityLauncher: ActivityResultLauncher<android.content.Intent>

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
        conversationListAdapter=ConversationListAdapter(context!!)
        conversationListAdapter.setActionListener(this::onConversationListItemAction)

        usageHeader=view.findViewById(R.id.usageHeader)

        conversationList=view.findViewById(R.id.conversationList)
        conversationList.adapter=conversationListAdapter

        messageInput=view.findViewById(R.id.messageInput)
        messageInput.setOnEditorActionListener(this::onMessageInputEditorAction)
        sendButton=view.findViewById(R.id.sendButton)
        sendButton.setOnClickListener(this::sendButtonClick)

        textInputActivityLauncher=registerForActivityResult(StartActivityForResult(), this::onTextInputActivityResult)
        }

    override fun onResume() {
        super.onResume()

        launch { adapter.mutex.withLock {
            conversationListAdapter.refresh(adapter.conversation)
            updateHeader()
            scrollToBottom()
            }}
        }

    fun sendButtonClick(v: View) {
        val message=messageInput.text?.toString() ?: return
        messageInput.text?.clear()
        sendButton.setClickable(false)
        launch { adapter.mutex.withLock {
            val userMessage=TextMessage(message)
            adapter.conversation.addMessage(userMessage)
            conversationListAdapter.refresh(adapter.conversation)
            updateHeader()
            scrollToBottom()

            try {
                val response=adapter.conversation.generateResponse()
                toastResponse(response)
                }
            catch (e: Exception) {
                toast(e.message ?: "Error: Unable to obtain response")
                }

            conversationListAdapter.refresh(adapter.conversation)
            updateHeader()
            scrollToBottom()
            sendButton.setClickable(true)
            }}
        }

    private fun onConversationListItemAction(action: ConversationListItemAction, position: Int) {
        when (action) {
            ConversationListItemAction.COPY -> copyMessage(position)
            ConversationListItemAction.EDIT -> startTextInputActivity(position, regenerateFrom=false)
            ConversationListItemAction.EDIT_REGENERATE_FROM -> startTextInputActivity(position, regenerateFrom=true)
            ConversationListItemAction.DELETE -> deleteMessage(position)
            ConversationListItemAction.REGENERATE_FROM -> regenerateFrom(position)
            ConversationListItemAction.STATS -> showStats(position)
            }
        }

    private fun copyMessage(position: Int) {
        val message=conversationListAdapter.getMessage(position) ?: return

        val clipboard=activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("VScan", message.text))
        toast("Copied")
        }

    private fun startTextInputActivity(position: Int, regenerateFrom: Boolean) {
        val message=conversationListAdapter.getMessage(position) ?: return

        val context=if (regenerateFrom) "edit-regenerate-from:$position" else "edit:$position"
        val intent=TextInputActivityInput("Edit message", message.text, context)
        .toIntent(activity!!)
        textInputActivityLauncher.launch(intent)
        }
    private fun onTextInputActivityResult(result: ActivityResult) {
        if (result.resultCode!=AppCompatActivity.RESULT_OK)
        return

        val output=TextInputActivityOutput.fromIntent(result.data, "ConversationFragment")

        val parts=output.context.split(":")
        if (parts.size!=2)
        return

        val regenerateFrom=parts[0]=="edit-regenerate-from"
        val position=parts[1].toIntOrNull() ?: return

        launch { adapter.mutex.withLock {
            val conversation=adapter.conversation
            if (position<0 || position>=conversation.messages.size)
            return@withLock

            conversation.replaceMessage(position, conversation.messages[position].withText(output.text))

            if (regenerateFrom) {
                conversation.removeMessagesFrom(position+1)
                conversationListAdapter.refresh(conversation)
                updateHeader()
                scrollToBottom()

                sendButton.setClickable(false)
                try {
                    val response=conversation.generateResponse()
                    toastResponse(response)
                    }
                catch (e: Exception) {
                    toast(e.message ?: "Error: Unable to obtain response")
                    }
                sendButton.setClickable(true)
                }

            conversationListAdapter.refresh(conversation)
            updateHeader()
            scrollToBottom()
            }}
        }

    private fun deleteMessage(position: Int) {
        launch { adapter.mutex.withLock {
            val conversation=adapter.conversation
            if (position<0 || position>=conversation.messages.size)
            return@withLock

            conversation.removeMessageAt(position)
            conversationListAdapter.refresh(conversation)
            updateHeader()
            }}
        }

    private fun regenerateFrom(position: Int) {
        launch { adapter.mutex.withLock {
            val conversation=adapter.conversation
            if (position<0 || position>=conversation.messages.size)
            return@withLock

            val message=conversation.messages[position]
            if (message is AssistantMessage)
            conversation.removeMessagesFrom(position)
            else
            conversation.removeMessagesFrom(position+1)

            conversationListAdapter.refresh(conversation)
            updateHeader()
            scrollToBottom()

            sendButton.setClickable(false)
            try {
                val response=conversation.generateResponse()
                toastResponse(response)
                }
            catch (e: Exception) {
                toast(e.message ?: "Error: Unable to obtain response")
                }
            sendButton.setClickable(true)

            conversationListAdapter.refresh(conversation)
            updateHeader()
            scrollToBottom()
            }}
        }

    private fun showStats(position: Int) {
        val message=conversationListAdapter.getMessage(position)
        if (message !is AssistantMessage)
        return

        val usage=message.usage
        val text="Prompt tokens: ${usage.promptTokens}\n"+
        "Completion tokens: ${usage.completionTokens}\n"+
        "Reasoning tokens: ${usage.reasoningTokens}\n"+
        "Total tokens: ${usage.totalTokens}"

        AlertDialog.Builder(activity!!)
        .setTitle("Message stats")
        .setMessage(text)
        .setPositiveButton("OK", null)
        .show()
        }

    private fun updateHeader() {
        val usage=adapter.conversation.usage
        usageHeader.text="P ${usage.promptTokens} · C ${usage.completionTokens} · R ${usage.reasoningTokens}"
        usageHeader.contentDescription="Prompt tokens: ${usage.promptTokens}, completion tokens: ${usage.completionTokens}, reasoning tokens: ${usage.reasoningTokens}"
        }
    private fun scrollToBottom() {
        val count=conversationListAdapter.itemCount
        if (count>0)
        conversationList.scrollToPosition(count-1)
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
    fun toastResponse(response: AssistantMessage) {
        if (!response.text.isEmpty())
        toast(response.text)
        else if (response.finishReason=="length")
        toast("Error: Reasoning exceeded the token limit")
        else
        toast("Error: Received empty output")
        }
    }
