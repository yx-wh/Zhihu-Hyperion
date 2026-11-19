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
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.viewmodel.MobileClientProvider
import com.github.zly2006.zhihu.viewmodel.NotificationViewModel
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import org.koin.compose.koinInject

/** TV 通知列表，复用手机端的 [NotificationViewModel]。 */
@Composable
fun TvNotificationScreen() {
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val mobileClient = koinInject<MobileClientProvider>()
    val navigator = LocalNavigator.current
    val viewModel: NotificationViewModel = viewModel { NotificationViewModel(mobileClient) }
    LaunchedEffect(Unit) {
        if (viewModel.allData.isEmpty() && !viewModel.isLoading) viewModel.refresh(environment)
    }

    TvListScreen(
        title = "通知",
        items = viewModel.allData,
        itemKey = { it.stableId },
        emptyText = when {
            viewModel.errorMessage != null -> "加载失败：${viewModel.errorMessage}"
            viewModel.isLoading -> "正在加载…"
            else -> "还没有新通知"
        },
        hints = listOf(
            TvHint(TvAction.Up, "移动"),
            TvHint(TvAction.Confirm, "打开"),
            TvHint(TvAction.Back, "返回"),
        ),
        onLoadMore = {
            if (!viewModel.isLoading && !viewModel.isEnd) viewModel.loadMore(environment)
        },
    ) { _, item, focusRequester ->
        val text = item.content
            ?.text
            .orEmpty()
            .let { if ('<' in it) Ksoup.parse(it).text() else it }
        TvListRow(
            title = item.detailTitle.ifBlank { item.content?.title.orEmpty() },
            subtitle = listOfNotNull(item.head?.author?.name, text, item.createdStr)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            trailing = if (item.isRead) null else "未读",
            focusRequester = focusRequester,
            onClick = {
                val url = item.target?.url?.takeIf { it.isNotBlank() } ?: item.content?.targetLink
                url?.let(::resolveContent)?.let { navigator.onNavigate(it) }
            },
        )
    }
}
