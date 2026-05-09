/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 * Suretype layout additions (c) 2025 WhiteFrost
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.core.view.allViews
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource

/**
 * Suretype 20-key compact keyboard layout.
 *
 * Each alphabet key shows two letters stacked vertically:
 *   - **Tap** sends the primary (top) letter.
 *   - **Swipe** sends the secondary (bottom) letter.
 *
 * Designed for predictive text input with Chinese (Pinyin) and English.
 * The reduced key count enables larger keys for single-thumb typing.
 *
 * Layout (5 columns × 4 rows):
 * ```
 *  QW   ER   TY   UI   OP
 *  AS   DF   GH   JK   L
 *  ⇧    ZX   CV   BN   M    ⌫
 *  ?123  ,   🌐   [  space  ]  .   ↵
 * ```
 */
@SuppressLint("ViewConstructor")
class SuretypeKeyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout) {

    enum class CapsState { None, Once, Lock }

    companion object {
        const val Name = "Suretype"

        val Layout: List<List<KeyDef>> = listOf(
            // Row 1: QW ER TY UI OP  (swipe up → 1-5, swipe down → shifted)
            listOf(
                SuretypeKey("Q", "W", swipeUp = "1", swipeDown = "!"),
                SuretypeKey("E", "R", swipeUp = "2", swipeDown = "@"),
                SuretypeKey("T", "Y", swipeUp = "3", swipeDown = "#"),
                SuretypeKey("U", "I", swipeUp = "4", swipeDown = "$"),
                SuretypeKey("O", "P", swipeUp = "5", swipeDown = "%")
            ),
            // Row 2: AS DF GH JK L  (swipe up → 6-0, swipe down → shifted)
            listOf(
                SuretypeKey("A", "S", swipeUp = "6", swipeDown = "^"),
                SuretypeKey("D", "F", swipeUp = "7", swipeDown = "&"),
                SuretypeKey("G", "H", swipeUp = "8", swipeDown = "*"),
                SuretypeKey("J", "K", swipeUp = "9", swipeDown = "("),
                SuretypeKey("L", "L", swipeUp = "0", swipeDown = ")", popup = arrayOf(
                    KeyDef.Popup.Preview("L"),
                    KeyDef.Popup.Keyboard("L")
                ))
            ),
            // Row 3: ⇧ ZX CV BN M ⌫  (swipe up/down → math/logic operators)
            listOf(
                CapsKey(),
                SuretypeKey("Z", "X", percentWidth = 0.175f, swipeUp = "+", swipeDown = "~"),
                SuretypeKey("C", "V", percentWidth = 0.175f, swipeUp = "-", swipeDown = "`"),
                SuretypeKey("B", "N", percentWidth = 0.175f, swipeUp = "=", swipeDown = "|"),
                SuretypeKey("M", "M", percentWidth = 0.175f, swipeUp = "/", swipeDown = "\\", popup = arrayOf(
                    KeyDef.Popup.Preview("M"),
                    KeyDef.Popup.Keyboard("M")
                )),
                BackspaceKey()
            ),
            // Row 4: ?123(long=toggle) , 🌐 space . ↵
            listOf(
                // Tap → NumberKeyboard, Long-press → toggle Suretype ↔ Text
                object : KeyDef(
                    KeyDef.Appearance.Text("?123", textSize = 16f, textStyle = android.graphics.Typeface.BOLD,
                        percentWidth = 0.15f, variant = KeyDef.Appearance.Variant.Alternative),
                    setOf(
                        KeyDef.Behavior.Press(KeyAction.LayoutSwitchAction(NumberKeyboard.Name)),
                        KeyDef.Behavior.LongPress(KeyAction.LayoutSwitchAction(KeyboardWindow.TOGGLE_LAYOUT))
                    )
                ) {},
                CommaKey(0.1f, KeyDef.Appearance.Variant.Alternative),
                LanguageKey(),
                SpaceKey(),
                SymbolKey(".", 0.1f, KeyDef.Appearance.Variant.Alternative),
                ReturnKey()
            )
        )
    }

    val caps: ImageKeyView by lazy { findViewById(R.id.button_caps) }
    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    private val showLangSwitchKey = AppPrefs.getInstance().keyboard.showLangSwitchKey

    @androidx.annotation.Keep
    private val showLangSwitchKeyListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        updateLangSwitchKey(v)
    }

    private val keepLettersUppercase by AppPrefs.getInstance().keyboard.keepLettersUppercase

    init {
        updateLangSwitchKey(showLangSwitchKey.getValue())
        showLangSwitchKey.registerOnChangeListener(showLangSwitchKeyListener)
    }

    private val textKeys: List<TextKeyView> by lazy {
        allViews.filterIsInstance(TextKeyView::class.java).toList()
    }

    private var capsState: CapsState = CapsState.None

    // For keys where the secondary letter is more common in Chinese pinyin,
    // tap sends the secondary instead of the primary.
    // Primary → Secondary mapping (only keys that need swapping):
    private val pinyinPreferredKey: Map<String, String> = mapOf(
        "Q" to "W",  // W is far more common in pinyin initials
        "T" to "Y",  // Y is the most frequent pinyin initial
        "U" to "I",  // I appears in more syllables
        "G" to "H",  // H appears in zh/ch/sh + standalone
        "Z" to "X",  // X is a top-3 pinyin initial
        "B" to "N",  // N is very common in finals (an/en/in/un/ang/eng/ing)
    )

    private fun transformAlphabet(c: String): String {
        return when (capsState) {
            CapsState.None -> c.lowercase()
            else -> c.uppercase()
        }
    }

    private var punctuationMapping: Map<String, String> = mapOf()
    private fun transformPunctuation(p: String) = punctuationMapping.getOrDefault(p, p)

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        var transformed = action
        when (action) {
            is KeyAction.DirectKeyAction -> when (source) {
                KeyActionListener.Source.Keyboard -> {
                    transformed = when (capsState) {
                        CapsState.None -> KeyAction.FcitxKeyAction(action.act.lowercase())
                        CapsState.Once -> {
                            switchCapsState()
                            KeyAction.FcitxKeyAction(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.Shift)
                            )
                        }
                        CapsState.Lock -> KeyAction.FcitxKeyAction(
                            act = action.act.uppercase(),
                            states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                        )
                    }
                }
                else -> {}
            }
            is KeyAction.FcitxKeyAction -> when (source) {
                KeyActionListener.Source.Keyboard -> {
                    val act = action.act
                    // For dual-letter keys, send the pinyin-better letter on tap
                    pinyinPreferredKey[act]?.let { better ->
                        transformed = action.copy(act = better)
                    }

                    // Apply caps state to the action
                    if (transformed is KeyAction.FcitxKeyAction) {
                        transformed = when (capsState) {
                            CapsState.None -> transformed.copy(act = transformed.act.lowercase())
                            CapsState.Once -> {
                                switchCapsState()
                                transformed.copy(
                                    act = transformed.act.uppercase(),
                                    states = KeyStates(KeyState.Virtual, KeyState.Shift)
                                )
                            }
                            CapsState.Lock -> transformed.copy(
                                act = transformed.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                            )
                        }
                    }
                }
                KeyActionListener.Source.Popup -> {
                    if (capsState == CapsState.Once) {
                        switchCapsState()
                    }
                }
            }
            is KeyAction.CapsAction -> switchCapsState(action.lock)
            is KeyAction.LayoutSwitchAction -> {
            }
            else -> {}
        }
        super.onAction(transformed, source)
    }

    override fun onAttach() {
        capsState = CapsState.None
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        punctuationMapping = mapping
        updatePunctuationKeys()
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = buildString {
            append(ime.displayName)
            ime.subMode.run { label.ifEmpty { name.ifEmpty { null } } }?.let { append(" ($it)") }
        }
        if (capsState != CapsState.None) {
            switchCapsState()
        }
    }

    private fun transformPopupPreview(c: String): String {
        if (c.length != 1) return c
        if (c[0].isLetter()) return transformAlphabet(c)
        return transformPunctuation(c)
    }

    override fun onPopupAction(action: PopupAction) {
        val newAction = when (action) {
            is PopupAction.PreviewAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.PreviewUpdateAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.ShowKeyboardAction -> {
                val label = action.keyboard.label
                if (label.length == 1 && label[0].isLetter())
                    action.copy(keyboard = KeyDef.Popup.Keyboard(transformAlphabet(label)))
                else action
            }
            else -> action
        }
        super.onPopupAction(newAction)
    }

    private fun switchCapsState(lock: Boolean = false) {
        capsState =
            if (lock) {
                when (capsState) {
                    CapsState.Lock -> CapsState.None
                    else -> CapsState.Lock
                }
            } else {
                when (capsState) {
                    CapsState.None -> CapsState.Once
                    else -> CapsState.None
                }
            }
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    private fun updateCapsButtonIcon() {
        caps.img.apply {
            imageResource = when (capsState) {
                CapsState.None -> R.drawable.ic_capslock_none
                CapsState.Once -> R.drawable.ic_capslock_once
                CapsState.Lock -> R.drawable.ic_capslock_lock
            }
        }
    }

    private fun updateLangSwitchKey(visible: Boolean) {
        lang.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateAlphabetKeys() {
        textKeys.forEach {
            if (it.def !is KeyDef.Appearance.AltText) return
            it.mainText.text = it.def.displayText.let { str ->
                if (str.length != 1 || !str[0].isLetter()) return@forEach
                if (keepLettersUppercase) str.uppercase() else transformAlphabet(str)
            }
        }
    }

    private fun updatePunctuationKeys() {
        textKeys.forEach {
            if (it is AltTextKeyView) {
                it.def as KeyDef.Appearance.AltText
                it.altText.text = transformPunctuation(it.def.altText)
            } else {
                it.def as KeyDef.Appearance.Text
                it.mainText.text = it.def.displayText.let { str ->
                    if (str[0].run { isLetter() || isWhitespace() }) return@forEach
                    transformPunctuation(str)
                }
            }
        }
    }

}
