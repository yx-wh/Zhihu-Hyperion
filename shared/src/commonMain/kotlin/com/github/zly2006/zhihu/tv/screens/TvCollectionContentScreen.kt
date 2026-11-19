/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2026, eltavine <me@eltavine.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.tv.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.viewmodel.CollectionContentViewModel
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment

/** TV 收藏夹内容列表，复用手机端的 [CollectionContentViewModel]。 */
@Composable
fun TvCollectionContentScreen(collectionId: String) {
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val navigator = LocalNavigator.current
    val viewModel: CollectionContentViewModel = viewModel(key = "tv_collection_$collectionId") {
        CollectionContentViewModel(collectionId)
    }
    LaunchedEffect(collectionId) {
        if (viewModel.allData.isEmpty() && !viewModel.isLoading) viewModel.refresh(environment)
    }

    TvListScreen(
        title = "收藏内容",
        items = viewModel.allData,
        itemKey = { it.content.url },
        emptyText = when {
            viewModel.errorMessage != null -> "加载失败：${viewModel.errorMessage}"
            viewModel.isLoading -> "正在加载…"
            else -> "收藏夹还是空的"
        },
        hints = listOf(
            TvHint(TvAction.Up, "移动"),
            TvHint(TvAction.Confirm, "打开"),
            TvHint(TvAction.Back, "返回"),
        ),
        onLoadMore = {
            if (!viewModel.isLoading && !viewModel.isEnd) viewModel.loadMore(environment)
        },
    ) { _, item, focusRequester, onFocusChanged ->
        val content = item.content
        TvListRow(
            title = content.title,
            subtitle = listOfNotNull(content.author?.name, content.description(), content.excerpt)
                .joinToString(" · "),
            focusRequester = focusRequester,
            onFocusChanged = onFocusChanged,
            onClick = { content.navDestination?.let { navigator.onNavigate(it) } },
        )
    }
}
