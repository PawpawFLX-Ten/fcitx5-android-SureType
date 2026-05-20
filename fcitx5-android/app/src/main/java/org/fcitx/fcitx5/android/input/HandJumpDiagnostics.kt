/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump V3
 */
package org.fcitx.fcitx5.android.input

import org.fcitx.fcitx5.android.BuildConfig
import timber.log.Timber

object HandJumpDiagnostics {
    private const val TAG = "HandJump"

    val enabled: Boolean = BuildConfig.DEBUG

    fun log(message: String) {
        if (enabled) {
            Timber.tag(TAG).d(message)
        }
    }
}
