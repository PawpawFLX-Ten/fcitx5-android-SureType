/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 WhiteFrost Suretype
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.View
import androidx.annotation.Keep
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class T9Keyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "T9"

        val Layout: List<List<KeyDef>> = listOf(
            // Row 1: @ | 1 2 3 | *
            listOf(
                SymbolKey("@", 0.12f, KeyDef.Appearance.Variant.Alternative),
                T9DigitKey("1", "",
                    swipeUp = ".", swipeDown = "?", swipeLeft = ",", swipeRight = "!"
                ),
                T9DigitKey("2", "ABC",  swipeLeft = "A", swipeUp = "B", swipeRight = "C"),
                T9DigitKey("3", "DEF",  swipeLeft = "D", swipeUp = "E", swipeRight = "F"),
                SymbolKey("*", 0.12f, KeyDef.Appearance.Variant.Alternative)
            ),
            // Row 2: ? | 4 5 6 | !
            listOf(
                SymbolKey("?", 0.12f, KeyDef.Appearance.Variant.Alternative),
                T9DigitKey("4", "GHI",  swipeLeft = "G", swipeUp = "H", swipeRight = "I"),
                T9DigitKey("5", "JKL",  swipeLeft = "J", swipeUp = "K", swipeRight = "L"),
                T9DigitKey("6", "MNO",  swipeLeft = "M", swipeUp = "N", swipeRight = "O"),
                SymbolKey("!", 0.12f, KeyDef.Appearance.Variant.Alternative)
            ),
            // Row 3: # | 7 8 9 | ⌫ (backspace above return)
            listOf(
                SymbolKey("#", 0.12f, KeyDef.Appearance.Variant.Alternative),
                T9DigitKey("7", "PQRS", swipeLeft = "P", swipeUp = "Q", swipeRight = "R", swipeDown = "S"),
                T9DigitKey("8", "TUV",  swipeLeft = "T", swipeUp = "U", swipeRight = "V"),
                T9DigitKey("9", "WXYZ", swipeLeft = "W", swipeUp = "X", swipeRight = "Y", swipeDown = "Z"),
                BackspaceKey(0.12f, KeyDef.Appearance.Variant.Alternative)
            ),
            // Row 4: bottom toolbar (⌫ removed → . moved to its position)
            listOf(
                object : KeyDef(
                    KeyDef.Appearance.Text(
                        "?123",
                        textSize = 16f,
                        textStyle = Typeface.BOLD,
                        percentWidth = 0.12f,
                        variant = KeyDef.Appearance.Variant.Alternative
                    ),
                    setOf(
                        KeyDef.Behavior.Press(KeyAction.LayoutSwitchAction(NumberKeyboard.Name))
                    )
                ) {},
                CommaKey(0.1f, KeyDef.Appearance.Variant.Alternative),
                LanguageKey(),
                SpaceKey(),
                SymbolKey(".", 0.1f, KeyDef.Appearance.Variant.Alternative),
                ReturnKey(0.14f)
            )
        )
    }

    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    private val showLangSwitchKey = AppPrefs.getInstance().keyboard.showLangSwitchKey

    @Keep
    private val showLangSwitchKeyListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        updateLangSwitchKey(v)
    }

    init {
        updateLangSwitchKey(showLangSwitchKey.getValue())
        showLangSwitchKey.registerOnChangeListener(showLangSwitchKeyListener)
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = spaceBarLabel(ime)
    }

    private fun updateLangSwitchKey(visible: Boolean) {
        lang.visibility = if (visible) View.VISIBLE else View.GONE
    }

    @SuppressLint("MissingSuperCall")
    override fun onPopupAction(action: PopupAction) {
        // Allow menu popups (Return key mode selector), preview, and dismiss.
        // Suppress sub-keyboard popups that interfere with swipe gestures.
        if (action is PopupAction.PreviewAction
            || action is PopupAction.PreviewUpdateAction
            || action is PopupAction.DismissAction
            || action is PopupAction.ShowMenuAction
            || action is PopupAction.TriggerAction
            || action is PopupAction.ChangeFocusAction
        ) {
            super.onPopupAction(action)
        }
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        val transformed = when (action) {
            is KeyAction.DirectKeyAction -> when (source) {
                KeyActionListener.Source.Keyboard ->
                    KeyAction.FcitxKeyAction(action.act.lowercase())
                else -> action
            }
            else -> action
        }
        super.onAction(transformed, source)
    }
}
