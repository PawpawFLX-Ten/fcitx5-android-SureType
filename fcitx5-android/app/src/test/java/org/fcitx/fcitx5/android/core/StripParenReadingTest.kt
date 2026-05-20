/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 WhiteFrost Suretype
 */
package org.fcitx.fcitx5.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StripParenReadingTest {

    @Test
    fun stripFullwidthLatinTail() {
        assertEquals("哈哈", stripParenReadingTail("哈哈（haha）"))
    }

    @Test
    fun stripHalfwidthTail() {
        assertEquals("词", stripParenReadingTail("词(ci)"))
    }

    @Test
    fun stripBracketSquareTail() {
        assertEquals("测", stripParenReadingTail("测［pin yin］"))
    }

    @Test
    fun doesNotStripHanInsideParens() {
        assertEquals("注释（内含汉字）", stripParenReadingTail("注释（内含汉字）"))
    }

    @Test
    fun extractReadingFromTail() {
        assertEquals("ni hao", extractTrailingParenReading("你好（ni hao）"))
    }

    @Test
    fun extractReturnsNullWhenNoLatinReading() {
        assertNull(extractTrailingParenReading("注释（内含汉字）"))
    }
}
