/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025 WhiteFrost Suretype
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class DefaultKeyboardLayout(override val stringRes: Int) : ManagedPreferenceEnum {
    Suretype(R.string.keyboard_layout_suretype),
    Text(R.string.keyboard_layout_text)
}
