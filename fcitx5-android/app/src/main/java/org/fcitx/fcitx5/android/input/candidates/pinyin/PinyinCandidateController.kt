/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 WhiteFrost Suretype
 */
package org.fcitx.fcitx5.android.input.candidates.pinyin

import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent.CandidateListEvent
import org.fcitx.fcitx5.android.core.FcitxEvent.PagedCandidateEvent
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.isFcitxPlaceholder
import org.fcitx.fcitx5.android.core.shouldRestoreCachedCandidates
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.keyboard.InputModeId
import org.fcitx.fcitx5.android.input.keyboard.InputModeRegistry
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.UniqueComponent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler
import org.mechdancer.dependency.manager.must

/**
 * HandJump candidate UI: readings live in [PinyinBarComponent]; the horizontal strip shows Han text only.
 *
 * Primary data path is [CandidateListEvent] (merged "汉字 拼音" lines from virtual-keyboard bulk mode).
 * [PagedCandidateEvent] is used only when comments are present — empty paged payloads must not
 * overwrite a good bulk parse (common when Rime comments are stripped on device).
 *
 * UI restore after keyboard re-show uses [org.fcitx.fcitx5.android.core.FcitxAPI.candidateListCached]
 * replayed from [org.fcitx.fcitx5.android.input.InputView], not async engine polling.
 */
class PinyinCandidateController :
    UniqueComponent<PinyinCandidateController>(),
    Dependent,
    ManagedHandler by managedHandler(),
    InputBroadcastReceiver {

    private val fcitx by manager.fcitx()
    private val context by manager.context()
    private val pinyinBar: PinyinBarComponent by manager.must()
    private val horizontalCandidate: HorizontalCandidateComponent by manager.must()

    private var state = PinyinCandidateState()
    private var pinyinGroupingActive = false
    private var lastModeId: InputModeId? = null

    override fun onScopeSetupFinished(scope: DynamicScope) {
        pinyinBar.onGroupSelected = { selectedComment ->
            setActiveComment(selectedComment)
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        refreshGroupingActive()
        if (!pinyinGroupingActive) {
            clear()
            return
        }
        // Candidate UI is restored via InputView replay of Fcitx caches (see onStartHandleFcitxEvent
        // / startInput). Do not clear state here — that raced ahead of async engine polling.
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        if (ime.isFcitxPlaceholder(context)) return
        val mode = InputModeRegistry.handJumpModeFor(ime)
        val modeChanged = mode?.id != lastModeId
        if (mode != null) {
            lastModeId = mode.id
        }
        val wasActive = pinyinGroupingActive
        refreshGroupingActive(ime)
        if (!pinyinGroupingActive) {
            if (!wasActive) {
                state = PinyinCandidateState()
                pinyinBar.hide()
                clearHorizontalCandidates()
            } else {
                clear()
            }
            return
        }
        if (!wasActive || modeChanged) {
            PinyinCandidateFilter.clear()
            restoreFromFcitxCache()
        }
    }

    override fun onCandidateUpdate(data: CandidateListEvent.Data) {
        if (!pinyinGroupingActive) return
        if (data.candidates.isEmpty()) {
            if (fcitx.runImmediately { shouldRestoreCachedCandidates() }) return
            clear()
            return
        }
        val newAll = data.candidates.mapIndexedNotNull { index, line ->
            line.toPinyinCandidateOrNull(index)
        }
        val keptFilter = state.activeComment?.takeIf { comment ->
            newAll.any { it.normalizedComment == normalizePinyinComment(comment) }
        }
        state = state.copy(activeComment = keptFilter, allCandidates = newAll)
        PinyinCandidateFilter.syncActive(keptFilter)
        render()
    }

    override fun onPagedCandidateUpdate(data: PagedCandidateEvent.Data) {
        if (fcitx.runImmediately { inputMethodEntryCached }.isFcitxPlaceholder(context)) return
        if (!pinyinGroupingActive) {
            if (data.candidates.isEmpty()) {
                clearHorizontalCandidates()
            }
            return
        }
        if (data.candidates.isEmpty()) {
            if (fcitx.runImmediately { shouldRestoreCachedCandidates() }) return
            clear()
            return
        }
        val structured = data.candidates.mapIndexedNotNull { index, candidate ->
            val reading = HandJumpCandidateDisplay.groupingComment(
                candidate.text,
                candidate.comment
            )
            val han = HandJumpCandidateDisplay.displayText(candidate.text, reading)
            if (!HandJumpCandidateGlyphs.isRenderableHan(han)) return@mapIndexedNotNull null
            PinyinCandidate(
                text = han,
                comment = reading,
                originalIndex = index
            )
        }
        // Ignore paged updates that carry no readings (would wipe bulk-parsed state).
        if (structured.none { it.comment.isNotBlank() }) {
            return
        }
        val keptFilter = state.activeComment?.takeIf { comment ->
            structured.any { it.normalizedComment == normalizePinyinComment(comment) }
        }
        state = state.copy(activeComment = keptFilter, allCandidates = structured)
        PinyinCandidateFilter.syncActive(keptFilter)
        render()
    }

    private fun restoreFromFcitxCache() {
        if (!fcitx.runImmediately { shouldRestoreCachedCandidates() }) {
            clear()
            return
        }
        fcitx.runImmediately { candidateListCached }?.let { onCandidateUpdate(it) }
        fcitx.runImmediately { pagedCandidateCached }?.let { onPagedCandidateUpdate(it) }
    }

    private fun setActiveComment(comment: String?) {
        state = state.copy(activeComment = comment)
        PinyinCandidateFilter.setActive(comment)
        render()
    }

    private fun render() {
        val groups = state.groups()
        when {
            groups.isEmpty() -> {
                state = state.copy(activeComment = null)
                PinyinCandidateFilter.clear()
                renderAllCandidates()
                pinyinBar.hide()
            }
            else -> {
                // Always use the multi-chip bar (even for a single reading) so the UI
                // does not flicker between showInfoChip and updateGroups while typing.
                pinyinBar.updateGroups(groups, state.activeComment)
                if (state.activeComment != null) {
                    renderCandidates()
                } else {
                    renderAllCandidates()
                }
            }
        }
    }

    private fun renderCandidates() {
        val visibleCandidates = state.visibleCandidates()
        if (visibleCandidates.isEmpty()) {
            state = state.copy(activeComment = null)
            PinyinCandidateFilter.clear()
            pinyinBar.updateGroups(state.groups(), null)
            renderAllCandidates()
            return
        }
        horizontalCandidate.adapter.updatePinyinCandidates(
            visibleCandidates.take(UNFILTERED_CANDIDATE_LIMIT),
            state.allCandidates.size
        )
    }

    private fun clearHorizontalCandidates() {
        horizontalCandidate.adapter.updatePinyinCandidates(emptyList(), 0)
    }

    private fun renderAllCandidates() {
        if (state.allCandidates.isEmpty()) {
            clearHorizontalCandidates()
            return
        }
        val visibleCandidates = state.allCandidates.take(UNFILTERED_CANDIDATE_LIMIT)
        horizontalCandidate.adapter.updatePinyinCandidates(
            visibleCandidates,
            state.allCandidates.size
        )
    }

    private fun clear() {
        state = PinyinCandidateState()
        PinyinCandidateFilter.clear()
        clearHorizontalCandidates()
        pinyinBar.hide()
    }

    private fun refreshGroupingActive(ime: InputMethodEntry = fcitx.runImmediately { inputMethodEntryCached }) {
        pinyinGroupingActive = InputModeRegistry.pinyinGroupingEnabled(ime)
    }

    private fun String.toPinyinCandidateOrNull(index: Int): PinyinCandidate? {
        val reading = HandJumpCandidateDisplay.groupingComment(this, "")
        val han = HandJumpCandidateDisplay.sanitizeMergedLine(this)
        if (!HandJumpCandidateGlyphs.isRenderableHan(han)) return null
        return PinyinCandidate(
            text = han,
            comment = reading,
            originalIndex = index
        )
    }

    private companion object {
        const val UNFILTERED_CANDIDATE_LIMIT = 16
    }
}
