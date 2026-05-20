/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.expanded

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.CandidateItemUi
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import org.fcitx.fcitx5.android.input.candidates.pinyin.HandJumpCandidateDisplay

open class PagingCandidateViewAdapter(val theme: Theme) :
    PagingDataAdapter<PagedCandidateLine, CandidateViewHolder>(diffCallback) {

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<PagedCandidateLine>() {
            override fun areItemsTheSame(oldItem: PagedCandidateLine, newItem: PagedCandidateLine): Boolean {
                return oldItem.engineIndex == newItem.engineIndex
            }

            override fun areContentsTheSame(oldItem: PagedCandidateLine, newItem: PagedCandidateLine): Boolean {
                return oldItem == newItem
            }
        }
    }

    var offset = 0
        private set

    fun refreshWithOffset(offset: Int) {
        this.offset = offset
        refresh()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        return CandidateViewHolder(CandidateItemUi(parent.context, theme))
    }

    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        val item = getItem(position)!!
        val shown = HandJumpCandidateDisplay.stripForCandidateRow(item.displayText)
        holder.ui.text.text = shown
        holder.text = shown
        holder.idx = item.engineIndex
    }
}
