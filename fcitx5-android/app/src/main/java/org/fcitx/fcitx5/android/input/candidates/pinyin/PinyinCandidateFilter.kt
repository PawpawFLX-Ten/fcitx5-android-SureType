/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump V3
 */
package org.fcitx.fcitx5.android.input.candidates.pinyin

/**
 * Shared HandJump reading filter for horizontal + expanded candidate UIs.
 * Set when a PinyinBar chip is active; cleared when filter is dismissed.
 */
object PinyinCandidateFilter {
    var activeNormalizedComment: String? = null
        private set

    /** Bumped when chip filter changes; expanded list should [refresh] paging. */
    var revision: Int = 0
        private set

    var onFilterChanged: (() -> Unit)? = null

    fun setActive(comment: String?) {
        val next = comment?.let(::normalizePinyinComment)?.takeIf { it.isNotBlank() }
        if (next == activeNormalizedComment) return
        activeNormalizedComment = next
        notifyChanged()
    }

    fun clear() {
        if (activeNormalizedComment == null) return
        activeNormalizedComment = null
        notifyChanged()
    }

    private fun notifyChanged() {
        revision++
        onFilterChanged?.invoke()
    }

    /** Sync filter from controller without redundant pager refresh. */
    fun syncActive(comment: String?) {
        val next = comment?.let(::normalizePinyinComment)?.takeIf { it.isNotBlank() }
        if (next == activeNormalizedComment) return
        activeNormalizedComment = next
        notifyChanged()
    }

    fun matchesReading(reading: String): Boolean {
        val filter = activeNormalizedComment ?: return true
        if (reading.isBlank()) return false
        return normalizePinyinComment(reading) == filter
    }
}
