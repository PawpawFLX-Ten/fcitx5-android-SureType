/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 WhiteFrost Suretype
 */
package org.fcitx.fcitx5.android.input.candidates.pinyin

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.utils.alpha
import splitties.dimensions.dp

private class PinyinBarChipSlot(
    val comment: String,
    var group: PinyinGroup,
    val view: TextView,
)

class PinyinBarComponent : UniqueViewComponent<PinyinBarComponent, HorizontalScrollView>() {

    private val context by manager.context()
    private val theme by manager.theme()

    var onGroupSelected: ((String?) -> Unit)? = null

    override val view: HorizontalScrollView by lazy {
        PinyinBarView(context).apply {
            visibility = View.GONE
            onGroupClicked = { comment ->
                onGroupSelected?.invoke(comment)
            }
        }
    }

    fun updateGroups(groups: List<PinyinGroup>, selectedComment: String?) {
        (view as PinyinBarView).updateGroups(groups, selectedComment)
    }

    fun hide() {
        (view as PinyinBarView).hide()
    }

    fun showInfo(group: PinyinGroup) {
        (view as PinyinBarView).showInfoChip(group)
    }

    private inner class PinyinBarView(context: android.content.Context) : HorizontalScrollView(context) {

        var onGroupClicked: ((String?) -> Unit)? = null

        private val chipContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val chipSlots = mutableListOf<PinyinBarChipSlot>()
        private var selectedComment: String? = null
        private var touchActive = false
        private var dragging = false
        private var gestureDownX = 0f
        private var gestureDownY = 0f
        private var pendingChipIndex = -1
        private var pendingGroups: List<PinyinGroup>? = null
        private var pendingSelectedComment: String? = null
        private var lastAppliedGroups: List<PinyinGroup> = emptyList()

        init {
            overScrollMode = OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
            applyStripBackground()
            addView(chipContainer)
        }

        private fun applyStripBackground() {
            setBackgroundColor(
                if (ThemeManager.prefs.keyBorder.getValue()) Color.TRANSPARENT
                else theme.keyboardColor
            )
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchActive = true
                    dragging = false
                    gestureDownX = event.x
                    gestureDownY = event.y
                    pendingChipIndex = chipIndexAt(scrollX + event.x - paddingLeft)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        val dx = abs(event.x - gestureDownX)
                        val dy = abs(event.y - gestureDownY)
                        if (dx > touchSlop && dx >= dy) {
                            dragging = true
                        }
                    }
                    return if (dragging) super.onTouchEvent(event) else true
                }
                MotionEvent.ACTION_UP -> {
                    val handled = if (dragging) {
                        super.onTouchEvent(event)
                    } else {
                        val index = pendingChipIndex
                        if (index in chipSlots.indices) {
                            handleChipTap(index)
                        }
                        true
                    }
                    endGesture()
                    return handled
                }
                MotionEvent.ACTION_CANCEL -> {
                    val handled = if (dragging) super.onTouchEvent(event) else true
                    endGesture()
                    return handled
                }
            }
            return super.onTouchEvent(event)
        }

        private fun endGesture() {
            touchActive = false
            dragging = false
            pendingChipIndex = -1
            flushPendingUpdate()
        }

        private fun flushPendingUpdate() {
            val groups = pendingGroups ?: return
            val selected = pendingSelectedComment
            pendingGroups = null
            pendingSelectedComment = null
            applyGroupsUpdate(groups, selected)
        }

        private fun chipIndexAt(contentX: Float): Int {
            if (chipSlots.isEmpty()) return -1
            val x = contentX.toInt()
            chipSlots.forEachIndexed { index, slot ->
                val child = slot.view
                if (x >= child.left && x < child.right) return index
            }
            return -1
        }

        private fun handleChipTap(index: Int) {
            val comment = chipSlots.getOrNull(index)?.comment ?: return
            InputFeedbacks.hapticFeedback(this)
            onGroupClicked?.invoke(
                if (selectedComment == comment) null else comment
            )
        }

        fun updateGroups(groups: List<PinyinGroup>, selectedComment: String?) {
            if (touchActive) {
                pendingGroups = groups
                pendingSelectedComment = selectedComment
                return
            }
            applyGroupsUpdate(groups, selectedComment)
        }

        private fun applyGroupsUpdate(groups: List<PinyinGroup>, selectedComment: String?) {
            this.selectedComment = selectedComment
            if (groups.isEmpty()) {
                hide()
                return
            }
            val structureUnchanged = sameChipStructure(groups)
            val contentChanged = groupsContentChanged(groups)
            if (structureUnchanged) {
                groups.forEachIndexed { index, group ->
                    val slot = chipSlots[index]
                    slot.group = group
                    bindChip(slot.view, group, index, selectedComment)
                }
            } else {
                rebuildChips(groups, selectedComment)
            }
            visibility = View.VISIBLE
            lastAppliedGroups = groups
            if (!touchActive) {
                applyScrollAfterUpdate(selectedComment, contentChanged)
            }
        }

        /**
         * Unfiltered: return to the primary chip when composition updates (new keystrokes).
         * Filtered: keep the active reading chip visible.
         */
        private fun applyScrollAfterUpdate(selectedComment: String?, contentChanged: Boolean) {
            if (selectedComment != null) {
                scrollSelectedIntoView(selectedComment, smooth = true)
            } else if (contentChanged) {
                scrollToStart()
            }
        }

        private fun groupsContentChanged(groups: List<PinyinGroup>): Boolean {
            if (groups.size != lastAppliedGroups.size) return true
            return groups.indices.any { i ->
                val next = groups[i]
                val prev = lastAppliedGroups[i]
                next.comment != prev.comment ||
                    next.label != prev.label ||
                    next.count != prev.count
            }
        }

        private fun scrollToStart() {
            if (scrollX == 0) return
            scrollTo(0, 0)
        }

        private fun sameChipStructure(groups: List<PinyinGroup>): Boolean {
            if (groups.size != chipSlots.size) return false
            return groups.indices.all { i -> groups[i].comment == chipSlots[i].comment }
        }

        private fun rebuildChips(groups: List<PinyinGroup>, selectedComment: String?) {
            chipSlots.clear()
            chipContainer.removeAllViews()
            groups.forEachIndexed { index, group ->
                val chip = makeChip(group, index, selectedComment)
                chipSlots.add(PinyinBarChipSlot(group.comment, group, chip))
                chipContainer.addView(chip, chipLayoutParams())
            }
        }

        fun hide() {
            visibility = View.GONE
            chipContainer.removeAllViews()
            chipSlots.clear()
            selectedComment = null
            lastAppliedGroups = emptyList()
            pendingGroups = null
            pendingSelectedComment = null
        }

        fun showInfoChip(group: PinyinGroup) {
            if (touchActive) return
            chipSlots.clear()
            chipContainer.removeAllViews()
            val chip = makeInfoChip("${group.label}  ${group.count}")
            chipSlots.add(PinyinBarChipSlot(group.comment, group, chip))
            chipContainer.addView(chip, chipLayoutParams())
            visibility = View.VISIBLE
            lastAppliedGroups = listOf(group)
            scrollToStart()
        }

        private fun bindChip(chip: TextView, group: PinyinGroup, index: Int, selectedComment: String?) {
            val highlighted = group.comment == selectedComment
            val primary = selectedComment == null && index == 0
            val accent = primary || highlighted
            chip.text = "${group.label} ${group.count}"
            chip.setTextColor(if (accent) theme.accentKeyTextColor else theme.keyTextColor)
            chip.setTypeface(chip.typeface, if (accent) Typeface.BOLD else Typeface.NORMAL)
            chip.setBackgroundColor(
                if (accent) theme.accentKeyBackgroundColor
                else theme.altKeyBackgroundColor.alpha(UNSELECTED_CHIP_ALPHA)
            )
            chip.contentDescription = group.label
        }

        private fun makeChip(group: PinyinGroup, index: Int, selectedComment: String?): TextView =
            TextView(context).apply {
                bindChip(this, group, index, selectedComment)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(5))
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }

        private fun makeInfoChip(text: String): TextView =
            TextView(context).apply {
                this.text = text
                textSize = 14f
                setTextColor(theme.keyTextColor)
                typeface = Typeface.DEFAULT
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(5))
                isClickable = false
                isFocusable = false
                setBackgroundColor(theme.altKeyBackgroundColor.alpha(UNSELECTED_CHIP_ALPHA))
            }

        private fun scrollSelectedIntoView(selectedComment: String?, smooth: Boolean) {
            if (selectedComment == null) return
            val index = chipSlots.indexOfFirst { it.comment == selectedComment }
            if (index < 0) return
            val child = chipSlots.getOrNull(index)?.view ?: return
            val viewport = width - paddingLeft - paddingRight
            if (viewport <= 0) {
                post { scrollSelectedIntoView(selectedComment, smooth) }
                return
            }
            val targetX = when {
                child.left < scrollX -> child.left
                child.right > scrollX + viewport -> child.right - viewport
                else -> return
            }
            if (smooth) smoothScrollTo(targetX, 0) else scrollTo(targetX, 0)
        }

        private fun chipLayoutParams() =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }

    }

    companion object {
        const val HEIGHT = 38

        /** Unselected chip fill — subtle on [Theme.keyboardColor] strip, matches alt-key tone. */
        private const val UNSELECTED_CHIP_ALPHA = 0.45f
    }
}
