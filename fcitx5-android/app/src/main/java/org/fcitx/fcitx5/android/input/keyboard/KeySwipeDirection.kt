/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.keyboard

import kotlin.math.absoluteValue

/**
 * Maps accumulated swipe counts from [CustomGestureView] to a direction.
 * [totalX]/[totalY] are threshold crossing counts, not pixel deltas.
 */
fun resolveKeySwipeDirection(totalX: Int, totalY: Int): String? {
    if (totalX == 0 && totalY == 0) return null
    val absX = totalX.absoluteValue
    val absY = totalY.absoluteValue
    return if (absX >= absY) {
        when {
            totalX < 0 -> "left"
            totalX > 0 -> "right"
            else -> null
        }
    } else {
        when {
            totalY < 0 -> "up"
            totalY > 0 -> "down"
            else -> null
        }
    }
}
