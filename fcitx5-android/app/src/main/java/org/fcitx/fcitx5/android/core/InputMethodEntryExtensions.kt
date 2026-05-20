/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump / fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

import android.content.Context
import org.fcitx.fcitx5.android.R

/** Placeholder IME before JNI delivers the real [InputMethodEntry]. */
fun InputMethodEntry.isFcitxPlaceholder(context: Context): Boolean =
    uniqueName.isEmpty() && name == context.getString(R.string._not_available_)

/** fcitx5 built-in Android keyboard IME (Latin), e.g. `keyboard-us`. */
fun InputMethodEntry.isAndroidLatinKeyboardIme(): Boolean =
    uniqueName.startsWith("keyboard-")

/** Rime [ascii_mode] — globe 切英文时 subMode 为 Latin Mode / label 为 A。 */
fun InputMethodEntry.isRimeAsciiMode(): Boolean =
    subMode.label == "A" ||
        subMode.name.contains("Latin", ignoreCase = true) ||
        subMode.name.contains("ASCII", ignoreCase = true)
