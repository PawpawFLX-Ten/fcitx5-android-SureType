/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump V3
 */
package org.fcitx.fcitx5.android.input.candidates.pinyin

import org.junit.Assert.assertEquals
import org.junit.Test

class HandJumpCandidateDisplayTest {

    @Test
    fun displayTextStripsSpaceSeparatedReading() {
        assertEquals("我", HandJumpCandidateDisplay.displayText("我 wo", "wo"))
    }

    @Test
    fun sanitizeMergedLineStripsReading() {
        assertEquals("我取", HandJumpCandidateDisplay.sanitizeMergedLine("我取 wo qu"))
    }

    @Test
    fun groupingCommentFromMergedText() {
        assertEquals("wo qu", HandJumpCandidateDisplay.groupingComment("我取 wo qu", ""))
    }

    @Test
    fun displayTextStripsHalfwidthParenReading() {
        assertEquals("词", HandJumpCandidateDisplay.displayText("词(ci)", ""))
    }

    @Test
    fun displayTextStripsFullwidthParenReading() {
        assertEquals("哈哈", HandJumpCandidateDisplay.displayText("哈哈（haha）", ""))
    }

    @Test
    fun displayTextStripsSquareBracketReading() {
        assertEquals("测", HandJumpCandidateDisplay.displayText("测［pin yin］", ""))
    }

    @Test
    fun displayTextStripsReadingFromSeparateComment() {
        assertEquals("时态", HandJumpCandidateDisplay.displayText("时态", "shi tai"))
    }

    @Test
    fun sanitizeMergedLineStripsFrostFullwidthBracketComment() {
        assertEquals("脸相", HandJumpCandidateDisplay.sanitizeMergedLine("脸相 ［lian xiang］"))
    }

    @Test
    fun sanitizeMergedLineStripsPlainSpaceSeparatedComment() {
        assertEquals("脸相", HandJumpCandidateDisplay.sanitizeMergedLine("脸相 lian xiang"))
    }

    @Test
    fun stripForCandidateRowStripsShortSyllablePairs() {
        assertEquals("无策", HandJumpCandidateDisplay.stripForCandidateRow("无策 wu ce"))
        assertEquals("其", HandJumpCandidateDisplay.stripForCandidateRow("其 qi"))
        assertEquals("去厕所", HandJumpCandidateDisplay.stripForCandidateRow("去厕所"))
    }
}
