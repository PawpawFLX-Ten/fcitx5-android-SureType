/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 WhiteFrost Suretype
 */
package org.fcitx.fcitx5.android.input.candidates.pinyin

data class PinyinCandidate(
    val text: String,
    val comment: String,
    val originalIndex: Int,
    val normalizedComment: String = normalizePinyinComment(comment)
)

data class PinyinGroup(
    val comment: String,
    val label: String,
    val count: Int
)

data class PinyinCandidateState(
    val allCandidates: List<PinyinCandidate> = emptyList(),
    val activeComment: String? = null
) {
    fun groups(): List<PinyinGroup> {
        val bestComment = allCandidates.firstOrNull()
            ?.normalizedComment
            ?.takeIf { it.isNotBlank() }
        return allCandidates
            .asSequence()
            .map { it.normalizedComment }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.key == bestComment }
                    .thenByDescending { it.value }
            )
            .map { (comment, count) ->
                PinyinGroup(
                    comment = comment,
                    label = comment.replace(" ", "·"),
                    count = count
                )
            }
    }

    fun visibleCandidates(): List<PinyinCandidate> {
        val filter = activeComment?.let(::normalizePinyinComment)?.takeIf { it.isNotBlank() }
            ?: return allCandidates
        return allCandidates.filter { it.normalizedComment == filter }
    }
}

internal fun normalizePinyinComment(comment: String): String =
    comment.trim()
        .removeSurrounding("［", "］")
        .trim()

/**
 * Strips the Rime comment that [always_show_comments] appends to candidate text.
 * Handles various bracket/parenthesis wrappers that Rime may use.
 * Example: "哈哈哈（hahaha）" → "哈哈哈", "你好［ni hao］" → "你好"
 */
internal fun String.stripRimeComment(comment: String): String {
    if (comment.isBlank()) return this
    val escaped = Regex.escape(comment.trim())
    return this.replace(Regex("""\s*[（(［\[]\s*$escaped\s*[）)\]］\]]$"""), "")
}
