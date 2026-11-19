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
import com.github.zly2006.zhihu.navigation.FollowUserUpdates
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.viewmodel.feed.FollowViewModel
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import org.koin.compose.koinInject

/** 某个关注账号的最近动态，复用手机端的 FollowViewModel 与分页逻辑。 */
@Composable
fun TvUserUpdatesScreen(destination: FollowUserUpdates) {
    val settings = koinInject<SettingsStore>()
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val navigator = LocalNavigator.current
    val model: FollowViewModel = viewModel(key = "tv_follow_updates_${destination.id}") {
        FollowViewModel(settings, destination.urlToken.ifBlank { destination.id })
    }
    LaunchedEffect(model) {
        if (model.displayItems.isEmpty() && !model.isLoading && !model.isEnd) model.loadMore(environment)
    }

    TvListScreen(
        title = destination.name.takeIf { it.isNotBlank() }?.let { "${it}的更新" } ?: "用户更新",
        items = model.displayItems,
        itemKey = { it.stableKey },
        emptyText = "该用户暂时没有更新",
        hints = listOf(
            TvHint(TvAction.Up, "移动"),
            TvHint(TvAction.Confirm, "打开"),
            TvHint(TvAction.Back, "返回"),
        ),
        onLoadMore = { if (!model.isLoading && !model.isEnd) model.loadMore(environment) },
    ) { _, item, focusRequester, onFocusChanged ->
        TvListRow(
            title = item.title,
            subtitle = listOfNotNull(item.authorName, item.summary, item.details)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            focusRequester = focusRequester,
            onFocusChanged = onFocusChanged,
            onClick = { item.navDestination?.let { navigator.onNavigate(it) } },
        )
    }
}
