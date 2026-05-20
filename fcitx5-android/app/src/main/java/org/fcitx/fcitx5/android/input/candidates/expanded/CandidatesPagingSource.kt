/*

 * SPDX-License-Identifier: LGPL-2.1-or-later

 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors

 */

package org.fcitx.fcitx5.android.input.candidates.expanded



import androidx.paging.PagingSource

import androidx.paging.PagingState

import org.fcitx.fcitx5.android.daemon.FcitxConnection

import org.fcitx.fcitx5.android.input.candidates.pinyin.HandJumpCandidateDisplay
import org.fcitx.fcitx5.android.input.candidates.pinyin.HandJumpCandidateGlyphs
import org.fcitx.fcitx5.android.input.candidates.pinyin.PinyinCandidateFilter

import timber.log.Timber



data class PagedCandidateLine(

    val displayText: String,

    val engineIndex: Int

)



/**

 * Loads engine candidates for the expanded grid/list via [FcitxConnection.getCandidates].

 *

 * Unlike the horizontal strip ([CandidateListEvent] → [PinyinCandidateController]), this path

 * must strip Rime "text + comment" itself and honor [PinyinCandidateFilter] when a chip is active.

 */

class CandidatesPagingSource(

    val fcitx: FcitxConnection,

    val total: Int,

    val offset: Int

) : PagingSource<Int, PagedCandidateLine>() {



    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PagedCandidateLine> {

        val startIndex = params.key ?: offset

        val pageSize = params.loadSize

        Timber.d("getCandidates(offset=$startIndex, limit=$pageSize)")

        val page = fcitx.runOnReady {

            val raw = getCandidates(startIndex, pageSize)

            raw.mapIndexedNotNull { index, line ->

                val engineIndex = startIndex + index

                val reading = HandJumpCandidateDisplay.groupingComment(line, "")

                if (!PinyinCandidateFilter.matchesReading(reading)) {

                    return@mapIndexedNotNull null

                }

                val han = HandJumpCandidateDisplay.stripForCandidateRow(line)
                if (!HandJumpCandidateGlyphs.isRenderableHan(han)) {
                    return@mapIndexedNotNull null
                }
                PagedCandidateLine(
                    displayText = han,
                    engineIndex = engineIndex
                )

            }.toTypedArray()

        }

        val prevKey = if (startIndex >= pageSize) startIndex - pageSize else null

        val nextKey = if (total > 0) {

            if (startIndex + pageSize + 1 >= total) null else startIndex + pageSize

        } else {

            if (page.size < pageSize) null else startIndex + pageSize

        }

        return LoadResult.Page(page.toList(), prevKey, nextKey)

    }



    override fun getRefreshKey(state: PagingState<Int, PagedCandidateLine>) = null

}

