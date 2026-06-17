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

package com.rastislavkish.vscan.ui.mainactivity

enum class ConversationListItemAction(val label: String) {
    COPY("Copy"),
    EDIT("Edit"),
    EDIT_REGENERATE_FROM("Edit and regenerate from this point"),
    DELETE("Delete"),
    REGENERATE_FROM("Regenerate from this point"),
    STATS("Stats"),
    }
