/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump V3
 */
package org.fcitx.fcitx5.android.input.candidates.pinyin

import android.text.TextPaint

/**
 * Drops dictionary entries whose Han characters are not covered by the system font
 * (otherwise AutoScaleTextView shows tofu boxes in the candidate grid).
 */
object HandJumpCandidateGlyphs {

    private val paint = TextPaint().apply {
        textSize = 20f
    }

    fun isRenderableHan(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            if (!paint.hasGlyph(String(Character.toChars(cp)))) {
                return false
            }
            i += Character.charCount(cp)
        }
        return true
    }
}
