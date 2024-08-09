package com.rastislavkish.vscan.core

import kotlinx.serialization.*

@Serializable
enum class LLM {
    GPT_4O,
    GPT_4O_MINI;

    val identifier: String
    get() = when (this) {
        LLM.GPT_4O -> "gpt-4o"
        LLM.GPT_4O_MINI -> "gpt-4o-mini"
        }

    }
