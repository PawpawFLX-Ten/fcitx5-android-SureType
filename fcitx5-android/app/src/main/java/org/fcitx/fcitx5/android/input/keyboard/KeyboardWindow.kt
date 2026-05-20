/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.isAndroidLatinKeyboardIme
import org.fcitx.fcitx5.android.core.isFcitxPlaceholder
import org.fcitx.fcitx5.android.core.isRimeAsciiMode
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

class KeyboardWindow : InputWindow.SimpleInputWindow<KeyboardWindow>(), EssentialWindow,
    InputBroadcastReceiver {

    private val service by manager.inputMethodService()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val popup: PopupComponent by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    companion object : EssentialWindow.Key

    override val key: EssentialWindow.Key
        get() = KeyboardWindow

    override fun enterAnimation(lastWindow: InputWindow) = Slide().apply {
        slideEdge = Gravity.BOTTOM
    }.takeIf {
        lastWindow !is PickerWindow
    }

    override fun exitAnimation(nextWindow: InputWindow) =
        super.exitAnimation(nextWindow).takeIf {
            nextWindow !is PickerWindow
        }

    private lateinit var keyboardView: FrameLayout

    private val keyboards: HashMap<String, BaseKeyboard> by lazy {
        hashMapOf(
            TextKeyboard.Name to TextKeyboard(context, theme),
            SuretypeKeyboard.Name to SuretypeKeyboard(context, theme),
            T9Keyboard.Name to T9Keyboard(context, theme),
            NumberKeyboard.Name to NumberKeyboard(context, theme)
        )
    }
    private var currentKeyboardName = ""
    private var lastSymbolType: String by AppPrefs.getInstance().internal.lastSymbolLayout

    /** 上次中文字母键盘（Suretype/T9/QWERTY），globe 从英文切回时恢复。 */
    private var lastAlphabetKeyboardName: String =
        InputModeRegistry.modeForSchemaId("rime_frost_suretype")?.keyboardName
            ?: SuretypeKeyboard.Name

    private val currentKeyboard: BaseKeyboard? get() = keyboards[currentKeyboardName]

    private val keyActionListener = KeyActionListener { it, source ->
        if (it is KeyAction.LayoutSwitchAction) {
            val target = when {
                it.act in InputModeRegistry.alphabetKeyboardNames &&
                    currentKeyboardName !in InputModeRegistry.alphabetKeyboardNames ->
                    lastAlphabetKeyboardName
                else -> it.act
            }
            switchLayout(target)
        } else {
            commonKeyActionListener.listener.onKeyAction(it, source)
        }
    }

    private val popupActionListener: PopupActionListener by lazy {
        popup.listener
    }

    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        attachLayout(lastAlphabetKeyboardName)
        return keyboardView
    }

    private fun detachCurrentLayout() {
        currentKeyboard?.also {
            it.onDetach()
            keyboardView.removeView(it)
            it.keyActionListener = null
            it.popupActionListener = null
        }
    }

    private fun attachLayout(target: String) {
        currentKeyboardName = target
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
            it.onAttach()
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            val ime = fcitx.runImmediately { inputMethodEntryCached }
            if (!ime.isFcitxPlaceholder(context)) {
                it.onInputMethodUpdate(ime)
            }
        }
    }

    fun switchLayout(to: String, remember: Boolean = true) {
        val target = to.ifEmpty { lastSymbolType }
        ContextCompat.getMainExecutor(service).execute {
            if (keyboards.containsKey(target)) {
                if (remember && target !in InputModeRegistry.alphabetKeyboardNames) {
                    lastSymbolType = target
                }
                if (target == currentKeyboardName) return@execute
                detachCurrentLayout()
                attachLayout(target)
                if (target in InputModeRegistry.alphabetKeyboardNames) {
                    lastAlphabetKeyboardName = target
                    ensureChineseMode()
                }
                if (windowManager.isAttached(this)) {
                    notifyBarLayoutChanged()
                }
            } else {
                if (remember) {
                    lastSymbolType = PickerWindow.Key.Symbol.name
                }
                windowManager.attachWindow(PickerWindow.Key.Symbol)
            }
        }
    }

    /** 从英文 (ascii) 切回中文键盘壳时，清除 Rime ascii_mode（见 default.yaml Shift+space）。 */
    private fun ensureChineseMode() {
        val ime = fcitx.runImmediately { inputMethodEntryCached }
        if (ime.isRimeAsciiMode()) {
            commonKeyActionListener.listener.onKeyAction(
                KeyAction.FcitxKeyAction(
                    act = " ",
                    states = KeyStates(KeyState.Virtual, KeyState.Shift),
                    code = 0
                ),
                KeyActionListener.Source.Keyboard
            )
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        var targetLayout = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
            InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
            else -> lastAlphabetKeyboardName
        }
        val ime = fcitx.runImmediately { inputMethodEntryCached }
        if (!ime.isFcitxPlaceholder(context)) {
            targetLayout = InputModeRegistry.resolveKeyboardShell(ime, lastAlphabetKeyboardName)
            if (!ime.isRimeAsciiMode() && targetLayout in InputModeRegistry.alphabetKeyboardNames) {
                lastAlphabetKeyboardName = targetLayout
            }
            if (ime.isAndroidLatinKeyboardIme() && targetLayout in InputModeRegistry.alphabetKeyboardNames) {
                targetLayout = TextKeyboard.Name
                lastAlphabetKeyboardName = TextKeyboard.Name
            }
        }
        switchLayout(targetLayout, remember = false)
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        if (ime.isFcitxPlaceholder(context)) return
        currentKeyboard?.onInputMethodUpdate(ime)

        val target = InputModeRegistry.resolveKeyboardShell(ime, lastAlphabetKeyboardName)
        if (!ime.isRimeAsciiMode() && target in InputModeRegistry.alphabetKeyboardNames) {
            lastAlphabetKeyboardName = target
        }
        if (ime.isAndroidLatinKeyboardIme() && currentKeyboardName in InputModeRegistry.alphabetKeyboardNames) {
            lastAlphabetKeyboardName = TextKeyboard.Name
            switchLayout(TextKeyboard.Name, remember = false)
            return
        }
        if (target != currentKeyboardName) {
            switchLayout(target, remember = false)
        }
    }

    override fun onStatusAreaUpdate(actions: Array<Action>) {
        val ime = fcitx.runImmediately { inputMethodEntryCached }
        if (!ime.isFcitxPlaceholder(context)) {
            onImeUpdate(ime)
        }
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        currentKeyboard?.onPunctuationUpdate(mapping)
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        currentKeyboard?.onReturnDrawableUpdate(resourceId)
    }

    override fun onAttached() {
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            it.onAttach()
        }
        notifyBarLayoutChanged()
    }

    override fun onDetached() {
        currentKeyboard?.let {
            it.onDetach()
            it.keyActionListener = null
            it.popupActionListener = null
        }
        popup.dismissAll()
    }

    private fun notifyBarLayoutChanged() {
        bar.onKeyboardLayoutSwitched(currentKeyboardName == NumberKeyboard.Name)
    }
}
