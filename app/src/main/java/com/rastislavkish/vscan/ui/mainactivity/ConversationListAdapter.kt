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

import android.content.Context

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView

import com.rastislavkish.vscan.R
import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.Message
import com.rastislavkish.vscan.core.openai.AssistantMessage

class ConversationListAdapter(context: Context): RecyclerView.Adapter<ConversationListAdapter.MessageViewHolder>() {

    private val settings=Settings.getInstance(context)

    private var messageList=mutableListOf<Message>()
    private val expandedPositions=mutableSetOf<Int>()

    private var actionListener: ((ConversationListItemAction, Int) -> Unit)?=null

    class MessageViewHolder(view: View): RecyclerView.ViewHolder(view) {

        val messageItem: View=view.findViewById(R.id.messageItem)
        val roleLabel: TextView=view.findViewById(R.id.roleLabel)
        val reasoningToggle: Button=view.findViewById(R.id.reasoningToggle)
        val reasoningContent: TextView=view.findViewById(R.id.reasoningContent)
        val contentText: TextView=view.findViewById(R.id.contentText)

        private val accessibilityActionIds=mutableListOf<Int>()

        init {
            messageItem.setOnLongClickListener(this::messageItem_longClick)
            reasoningToggle.setOnClickListener(this::reasoningToggle_click)
            }

        private fun adapter(): ConversationListAdapter? = bindingAdapter as ConversationListAdapter?

        fun bind(message: Message, expanded: Boolean) {
            val adapter=adapter() ?: return

            roleLabel.text=message.role.replaceFirstChar { it.uppercase() }

            if (message is AssistantMessage && !message.reasoning.isEmpty()) {
                reasoningToggle.visibility=View.VISIBLE
                reasoningToggle.text="Reasoning (${message.usage.reasoningTokens} tokens)"
                reasoningToggle.contentDescription="Reasoning (${message.usage.reasoningTokens} tokens), ${if (expanded) "expanded" else "collapsed"}"

                if (expanded) {
                    reasoningContent.visibility=View.VISIBLE
                    reasoningContent.text=message.reasoning
                    }
                else {
                    reasoningContent.visibility=View.GONE
                    }
                }
            else {
                reasoningToggle.visibility=View.GONE
                reasoningContent.visibility=View.GONE
                }

            if (message is AssistantMessage && message.text.isEmpty()) {
                if (message.finishReason=="length")
                contentText.text="Error: Reasoning exceeded the token limit"
                else
                contentText.text="Error: Received empty output"
                }
            else {
                contentText.text=message.text
                }

            bindAccessibilityActions(adapter, message)
            }

        private fun bindAccessibilityActions(adapter: ConversationListAdapter, message: Message) {
            for (id in accessibilityActionIds) {
                ViewCompat.removeAccessibilityAction(contentText, id)
                }
            accessibilityActionIds.clear()

            for (action in availableActions(message)) {
                val id=ViewCompat.addAccessibilityAction(contentText, action.label) { _, _ ->
                    invokeAction(action)
                    true
                    }
                accessibilityActionIds.add(id)
                }
            }

        private fun invokeAction(action: ConversationListItemAction) {
            val position=bindingAdapterPosition
            if (position==RecyclerView.NO_POSITION)
            return

            adapter()?.onAction(action, position)
            }

        fun messageItem_longClick(view: View): Boolean {
            val position=bindingAdapterPosition
            if (position==RecyclerView.NO_POSITION)
            return true

            val adapter=adapter() ?: return true
            val message=adapter.getMessage(position) ?: return true

            val popup=PopupMenu(view.context, view)
            val actions=availableActions(message)
            for ((index, action) in actions.withIndex()) {
                popup.menu.add(0, index, index, action.label)
                }
            popup.setOnMenuItemClickListener { item ->
                adapter.onAction(actions[item.itemId], position)
                true
                }
            popup.show()

            return true
            }
        fun reasoningToggle_click(view: View) {
            val position=bindingAdapterPosition
            if (position==RecyclerView.NO_POSITION)
            return

            adapter()?.toggleReasoning(position)
            }

        private fun availableActions(message: Message): List<ConversationListItemAction> {
            val actions=mutableListOf(
                ConversationListItemAction.COPY,
                ConversationListItemAction.EDIT,
                ConversationListItemAction.EDIT_REGENERATE_FROM,
                ConversationListItemAction.DELETE,
                ConversationListItemAction.REGENERATE_FROM,
                )

            if (message is AssistantMessage)
            actions.add(ConversationListItemAction.STATS)

            return actions
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view=LayoutInflater.from(parent.context)
        .inflate(R.layout.conversation_list_item, parent, false)

        return MessageViewHolder(view)
        }
    override fun onBindViewHolder(viewHolder: MessageViewHolder, position: Int) {
        viewHolder.bind(messageList[position], expandedPositions.contains(position))
        }
    override fun getItemCount() = messageList.size

    fun toggleReasoning(position: Int) {
        if (expandedPositions.contains(position))
        expandedPositions.remove(position)
        else
        expandedPositions.add(position)

        notifyItemChanged(position)
        }

    fun setActionListener(listener: ((ConversationListItemAction, Int) -> Unit)?) {
        actionListener=listener
        }
    fun onAction(action: ConversationListItemAction, position: Int) {
        actionListener?.invoke(action, position)
        }

    fun getMessage(position: Int): Message? {
        return messageList.getOrNull(position)
        }

    fun addMessage(message: Message) {
        messageList.add(message)

        notifyItemInserted(messageList.size-1)
        }
    fun refresh(conversation: Conversation) {
        messageList.clear()
        messageList.addAll(conversation.toMessageList())
        expandedPositions.clear()

        notifyDataSetChanged()
        }

    }
