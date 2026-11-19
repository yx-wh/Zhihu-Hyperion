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
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.viewmodel.CollectionsViewModel
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment

/** TV 收藏夹列表，复用手机端的 [CollectionsViewModel]。 */
@Composable
fun TvCollectionsScreen(urlToken: String) {
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val navigator = LocalNavigator.current
    val viewModel: CollectionsViewModel = viewModel(key = "tv_collections_$urlToken") {
        CollectionsViewModel(urlToken)
    }
    LaunchedEffect(urlToken) {
        if (viewModel.allData.isEmpty() && !viewModel.isLoading) viewModel.refresh(environment)
    }

    TvListScreen(
        title = "收藏夹",
        items = viewModel.allData,
        itemKey = { it.id },
        emptyText = when {
            viewModel.errorMessage != null -> "加载失败：${viewModel.errorMessage}"
            viewModel.isLoading -> "正在加载…"
            else -> "还没有收藏夹"
        },
        hints = listOf(
            TvHint(TvAction.Up, "移动"),
            TvHint(TvAction.Confirm, "打开"),
            TvHint(TvAction.Back, "返回"),
        ),
        onLoadMore = {
            if (!viewModel.isLoading && !viewModel.isEnd) viewModel.loadMore(environment)
        },
    ) { _, collection, focusRequester ->
        TvListRow(
            title = collection.title,
            subtitle = collection.description,
            trailing = "${collection.itemCount} 条",
            focusRequester = focusRequester,
            onClick = { navigator.onNavigate(CollectionContent(collectionId = collection.id)) },
        )
    }
}
