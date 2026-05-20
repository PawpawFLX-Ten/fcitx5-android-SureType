/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump V3
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputModeRegistryTest {

    @Test
    fun orderedModesDefineThreeUniqueRimeSchemas() {
        val modes = InputModeRegistry.orderedModes
        assertEquals(
            listOf(
                InputModeId.RIME_FROST_QWERTY,
                InputModeId.RIME_FROST_SURETYPE,
                InputModeId.RIME_FROST_T9
            ),
            modes.map { it.id }
        )
        assertEquals(modes.size, modes.map { it.schemaId }.toSet().size)
    }

    @Test
    fun resolvesBySchemaIdWhenSubModeIsLatin() {
        assertEquals(
            InputModeId.RIME_FROST_SURETYPE,
            InputModeRegistry.modeForSubModeNameOrSchemaId("Latin Mode", "rime_frost_suretype")?.id
        )
    }

    @Test
    fun pinyinGroupingFollowsRegistry() {
        assertTrue(
            InputModeRegistry.pinyinGroupingEnabled("白霜拼音", "rime_frost")
        )
        assertTrue(
            InputModeRegistry.pinyinGroupingEnabled("Latin Mode", "rime_frost_t9")
        )
    }

    @Test
    fun keyboardBinding() {
        assertNotNull(InputModeRegistry.modeForKeyboardName(SuretypeKeyboard.Name))
        assertEquals(
            "rime_frost_t9",
            InputModeRegistry.modeForKeyboardName(T9Keyboard.Name)?.schemaId
        )
    }

    @Test
    fun unknownSubModeNameReturnsNull() {
        assertNull(InputModeRegistry.modeForSubModeName("unknown"))
    }

    @Test
    fun unknownSchemaIdReturnsNull() {
        assertNull(InputModeRegistry.modeForSchemaId("unknown_schema"))
    }

    @Test
    fun unknownKeyboardNameReturnsNull() {
        assertNull(InputModeRegistry.modeForKeyboardName("???"))
    }

    @Test
    fun latinWithUnknownSchemaIdReturnsNull() {
        assertNull(InputModeRegistry.modeForSubModeNameOrSchemaId("Latin Mode", "unknown_schema"))
    }

    @Test
    fun handJumpRimeSchemaPrefixEnablesPinyinGrouping() {
        assertTrue(InputModeRegistry.pinyinGroupingEnabled("unknown", "rime_frost_legacy"))
        assertTrue(InputModeRegistry.isHandJumpRimeSchema("rime_frost_suretype"))
    }
}
