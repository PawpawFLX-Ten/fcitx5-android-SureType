/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump V3
 */
package org.fcitx.fcitx5.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FcitxCandidateCacheTest {

    @Test
    fun copyForCacheIsIndependent() {
        val original = FcitxEvent.CandidateListEvent.Data(
            total = 2,
            candidates = arrayOf("我 wo", "你 ni")
        )
        val copy = original.copyForCache()
        original.candidates[0] = "changed"
        assertEquals("我 wo", copy.candidates[0])
    }

    @Test
    fun shouldRestoreWhenCachePresentEvenWithoutPreedit() {
        assertTrue(
            shouldRestoreCachedCandidates(
                FcitxEvent.CandidateListEvent.Data(1, arrayOf("嘎 ga")),
                null
            )
        )
    }

    @Test
    fun shouldRestoreFalseWhenNoCache() {
        assertFalse(
            shouldRestoreCachedCandidates(null, null)
        )
    }
}
