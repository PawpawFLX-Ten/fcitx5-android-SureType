/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump V3
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.isRimeAsciiMode

enum class InputModeId { RIME_FROST_QWERTY, RIME_FROST_SURETYPE, RIME_FROST_T9 }

/**
 * Single source of truth: Rime [schemaId] + display [subModeName] + Android [keyboardName].
 */
data class InputMode(
    val id: InputModeId,
    val schemaId: String,
    val subModeName: String,
    val userLabel: String,
    val keyboardName: String,
    val pinyinGroupingEnabled: Boolean = false
)

object InputModeRegistry {
    val orderedModes = listOf(
        InputMode(
            InputModeId.RIME_FROST_QWERTY,
            schemaId = "rime_frost",
            subModeName = "白霜拼音",
            userLabel = "白霜拼音",
            keyboardName = TextKeyboard.Name,
            pinyinGroupingEnabled = true
        ),
        InputMode(
            InputModeId.RIME_FROST_SURETYPE,
            schemaId = "rime_frost_suretype",
            subModeName = "白霜双键",
            userLabel = "白霜双键",
            keyboardName = SuretypeKeyboard.Name,
            pinyinGroupingEnabled = true
        ),
        InputMode(
            InputModeId.RIME_FROST_T9,
            schemaId = "rime_frost_t9",
            subModeName = "白霜九键",
            userLabel = "白霜九键",
            keyboardName = T9Keyboard.Name,
            pinyinGroupingEnabled = true
        )
    )

    private val bySubModeName = orderedModes.associateBy { it.subModeName }
    private val bySchemaId = orderedModes.associateBy { it.schemaId }

    fun modeForSubModeName(subModeName: String): InputMode? =
        bySubModeName[subModeName]

    fun modeForSchemaId(schemaId: String): InputMode? =
        bySchemaId[schemaId]

    fun modeForSubModeNameOrSchemaId(subModeName: String, schemaId: String?): InputMode? =
        modeForSubModeName(subModeName)
            ?: schemaId?.let { modeForSchemaId(it) }

    fun handJumpModeFor(ime: InputMethodEntry): InputMode? =
        modeForSubModeNameOrSchemaId(ime.subMode.name, ime.schemaId)

    fun isHandJumpRimeSchema(schemaId: String?): Boolean =
        schemaId?.startsWith("rime_frost") == true

    fun modeForKeyboardName(keyboardName: String): InputMode? =
        orderedModes.firstOrNull { it.keyboardName == keyboardName }

    val alphabetKeyboardNames: Set<String> = orderedModes.map { it.keyboardName }.toSet()

    fun pinyinGroupingEnabled(subModeName: String, schemaId: String?): Boolean =
        modeForSubModeNameOrSchemaId(subModeName, schemaId)?.pinyinGroupingEnabled == true ||
            isHandJumpRimeSchema(schemaId)

    fun pinyinGroupingEnabled(ime: InputMethodEntry): Boolean =
        pinyinGroupingEnabled(ime.subMode.name, ime.schemaId)

    /**
     * 屏幕键盘壳：英文 → QWERTY；中文 → 方案对应 Suretype/T9/QWERTY。
     * [lastAlphabetKeyboard] 记住用户上次中文键盘（globe 切回时恢复）。
     */
    fun resolveKeyboardShell(ime: InputMethodEntry, lastAlphabetKeyboard: String): String {
        if (ime.isRimeAsciiMode()) {
            return TextKeyboard.Name
        }
        handJumpModeFor(ime)?.keyboardName?.let { return it }
        if (isHandJumpRimeSchema(ime.schemaId) && lastAlphabetKeyboard.isNotEmpty()) {
            return lastAlphabetKeyboard
        }
        return lastAlphabetKeyboard.ifEmpty { TextKeyboard.Name }
    }
}
