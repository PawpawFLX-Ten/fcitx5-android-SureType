/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 HandJump V3
 */
package org.fcitx.fcitx5.android.core

/**
 * Deep copies of the last candidate payloads for UI restore (keyboard re-show / handleEvents on).
 * Mirrors how [Fcitx.inputPanelCached] is kept in [Fcitx.handleFcitxEvent].
 * Cleared on [FcitxAPI.reset] and [FcitxEvent.CommitStringEvent], not when focus-out clears preedit.
 */
fun FcitxEvent.CandidateListEvent.Data.copyForCache(): FcitxEvent.CandidateListEvent.Data =
    FcitxEvent.CandidateListEvent.Data(total, candidates.copyOf())

fun FcitxEvent.PagedCandidateEvent.Data.copyForCache(): FcitxEvent.PagedCandidateEvent.Data =
    FcitxEvent.PagedCandidateEvent.Data(
        candidates = candidates.map { it.copy() }.toTypedArray(),
        cursorIndex = cursorIndex,
        layoutHint = layoutHint,
        hasPrev = hasPrev,
        hasNext = hasNext
    )

/**
 * Whether we still have a snapshot to replay. Intentionally does not require
 * [FcitxEvent.InputPanelEvent.Data.preedit]: focus-out often clears panel preedit while
 * composition is still active in the editor.
 */
fun shouldRestoreCachedCandidates(
    candidateList: FcitxEvent.CandidateListEvent.Data?,
    paged: FcitxEvent.PagedCandidateEvent.Data?,
): Boolean = candidateList != null || paged != null

fun FcitxAPI.shouldRestoreCachedCandidates(): Boolean =
    shouldRestoreCachedCandidates(candidateListCached, pagedCandidateCached)

fun FcitxAPI.isCompositionPreeditEmpty(): Boolean =
    clientPreeditCached.isEmpty() && inputPanelCached.preedit.isEmpty()

const val FCITX_CANDIDATE_RESTORE_LIMIT = 128
