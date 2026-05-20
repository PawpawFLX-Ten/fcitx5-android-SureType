/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump V3
 */
package org.fcitx.fcitx5.android.input.candidates.pinyin

import org.fcitx.fcitx5.android.core.extractTrailingParenReading
import org.fcitx.fcitx5.android.core.stripParenReadingTail

/**
 * Single place for HandJump candidate presentation rules:
 * - [displayText] / [sanitizeMergedLine]: candidate rows show Han text only
 * - [groupingComment]: PinyinBar chips use normalized readings from comment or text tail
 */
object HandJumpCandidateDisplay {

    private val trailingSpaceSeparatedReading = Regex(
        """[\s\u00A0\u3000]+([a-zA-Z][a-zA-Z0-9\s'·\-]*)$"""
    )

    /** Fallback when bracket/comment strip left a trailing pinyin run (Rime bulk / getCandidates). */
    private val trailingLatinReadingRun = Regex(
        """[\s\u00A0\u3000]+[a-zA-Z][a-zA-Z0-9\s'·\-]*$"""
    )

    /** True when [line] still looks like Rime "汉字 + 注音" merged for display. */
    fun looksLikeAnnotatedCandidate(line: String): Boolean =
        trailingLatinReadingRun.containsMatchIn(line) ||
            extractTrailingParenReading(line) != null

    fun groupingComment(text: String, comment: String): String =
        comment.trim().takeIf { it.isNotBlank() }
            ?: extractTrailingParenReading(text)
            ?: extractTrailingSpaceSeparatedReading(text).orEmpty()

    fun displayText(text: String, comment: String): String {
        val reading = groupingComment(text, comment)
        var out = text.stripRimeComment(reading)
        if (reading.isNotBlank()) {
            val tail = " ${reading.trim()}"
            if (out.endsWith(tail)) {
                out = out.removeSuffix(tail)
            }
        }
        return out.trim().stripTrailingLatinReadingRun().let { stripParenReadingTail(it) }
    }

    /** Safe to call on already-stripped Han; no-op when no trailing reading. */
    fun sanitizeMergedLine(line: String): String {
        val reading = extractTrailingParenReading(line)
            ?: extractTrailingSpaceSeparatedReading(line)
        return if (reading != null) {
            displayText(line, reading)
        } else {
            line.trim().stripTrailingLatinReadingRun().let { stripParenReadingTail(it) }
        }
    }

    private fun String.stripTrailingLatinReadingRun(): String {
        var t = this
        while (true) {
            val next = trailingLatinReadingRun.replace(t, "")
            if (next == t) return t
            t = next
        }
    }

    private fun extractTrailingSpaceSeparatedReading(text: String): String? {
        val m = trailingSpaceSeparatedReading.find(text.trim()) ?: return null
        return m.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    /**
     * Expanded candidate rows ([getCandidates] / paging). Safe on English-only lines (no-op).
     */
    fun stripForCandidateRow(line: String): String {
        if (!line.any { Character.isIdeographic(it.code) }) {
            return line.trim()
        }
        val once = sanitizeMergedLine(line)
        return if (looksLikeAnnotatedCandidate(once)) {
            once.stripTrailingLatinReadingRun().let { stripParenReadingTail(it) }
        } else {
            once
        }
    }
}
