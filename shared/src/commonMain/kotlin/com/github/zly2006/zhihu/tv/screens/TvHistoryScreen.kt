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
import com.github.zly2006.zhihu.data.HistoryStorage
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.viewmodel.feed.OnlineHistoryViewModel
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import org.koin.compose.koinInject

/** TV 在线历史列表，复用手机端的 [OnlineHistoryViewModel]。 */
@Composable
fun TvHistoryScreen() {
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val settings = koinInject<SettingsStore>()
    val history = koinInject<HistoryStorage>()
    val navigator = LocalNavigator.current
    val viewModel: OnlineHistoryViewModel = viewModel { OnlineHistoryViewModel(settings, history) }
    LaunchedEffect(Unit) {
        if (viewModel.displayItems.isEmpty() && !viewModel.isLoading) viewModel.refresh(environment)
    }

    TvListScreen(
        title = "历史",
        items = viewModel.displayItems,
        itemKey = { it.stableKey },
        emptyText = when {
            viewModel.errorMessage != null -> "加载失败：${viewModel.errorMessage}"
            viewModel.isLoading -> "正在加载…"
            else -> "还没有浏览记录"
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
        TvListRow(
            title = item.title,
            subtitle = listOfNotNull(item.authorName, item.details).joinToString(" · "),
            focusRequester = focusRequester,
            onFocusChanged = onFocusChanged,
            onClick = { item.navDestination?.let { navigator.onNavigate(it) } },
        )
    }
}
