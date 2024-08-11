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
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.rastislavkish.vscan.R
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.Message

class ConversationListAdapter: RecyclerView.Adapter<ConversationListAdapter.MessageViewHolder>() {

    private var messageList=mutableListOf<Message>()

    class MessageViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val itemTextView: TextView=view.findViewById(R.id.itemTextView)

        private var message: Message?=null

        fun bind(message: Message) {
            this.message=message

            itemTextView.text="${message.role}: ${message.text}"
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view=LayoutInflater.from(parent.context)
        .inflate(R.layout.conversation_list_item, parent, false)

        return MessageViewHolder(view)
        }
    override fun onBindViewHolder(viewHolder: MessageViewHolder, position: Int) {
        viewHolder.bind(messageList[position])
        }
    override fun getItemCount() = messageList.size

    fun addMessage(message: Message) {
        messageList.add(message)

        notifyItemInserted(messageList.size)
        }
    fun refresh(conversation: Conversation) {
        messageList.clear()
        messageList.addAll(conversation.toMessageList())

        notifyDataSetChanged()
        }

    }
