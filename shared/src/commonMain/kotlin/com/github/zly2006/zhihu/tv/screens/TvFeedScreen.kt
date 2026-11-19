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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.tv.components.TvActionPill
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.components.TvHints
import com.github.zly2006.zhihu.tv.focus.TvFocusable
import com.github.zly2006.zhihu.tv.focus.tvFocusSurface
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.ui.HomeScreenState
import com.github.zly2006.zhihu.ui.components.AppLoadingIndicator
import com.github.zly2006.zhihu.ui.rememberHomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.FollowViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HotListViewModel
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.notInterestedContentKey
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/** TV 信息流的三个来源；与左侧栏目一一对应。 */
enum class TvFeedKind(
    val title: String,
) {
    Home("推荐"),
    Follow("关注"),
    Hot("热榜"),
}

private val cardShape = RoundedCornerShape(20.dp)

/**
 * 手柄 / TV 模式的首页信息流：大卡片网格，复用手机端同一批 feed ViewModel 与推荐模式选择。
 *
 * 分页、过滤和推荐模式都不在这里重新实现；这里只负责十英尺布局、焦点与翻页触发。
 *
 * @param requestInitialFocus 应用刚进入 TV 模式时把焦点交给第一张卡片；切换栏目时由栏目栏保留焦点。
 */
@Composable
fun TvFeedScreen(
    kind: TvFeedKind,
    onOpenSearch: () -> Unit,
    requestInitialFocus: Boolean,
    onInitialFocusRequested: () -> Unit,
) {
    val settings = koinInject<SettingsStore>()
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = true)
    val userMessages = rememberUserMessageSink()
    val navigator = LocalNavigator.current
    val mode = remember(settings) {
        RecommendationMode.entries.find { it.key == settings.getString("recommendationMode", RecommendationMode.MIXED.key) }
            ?: RecommendationMode.MIXED
    }
    val viewModel: BaseFeedViewModel = when (kind) {
        TvFeedKind.Home -> rememberHomeFeedViewModel(mode)
        TvFeedKind.Follow -> viewModel { FollowViewModel(settings) }
        TvFeedKind.Hot -> viewModel { HotListViewModel(settings) }
    }

    // 首页需要在最终展示层过滤「不感兴趣」，与手机端使用同一份本地记录。
    val excludedKeys = if (kind == TvFeedKind.Home) {
        val database = koinInject<ContentFilterDatabase>()
        val notInterestedDao = remember(database) { database.notInterestedContentDao() }
        viewModel<HomeScreenState> { HomeScreenState(notInterestedDao) }.notInterestedKeys
    } else {
        null
    }
    val items by remember(viewModel, excludedKeys) {
        derivedStateOf {
            if (excludedKeys == null) {
                viewModel.displayItems.toList()
            } else {
                viewModel.displayItems.filterNot { it.notInterestedContentKey() in excludedKeys }
            }
        }
    }

    val gridState = rememberLazyGridState()
    var restoreFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }
    val pendingFocusRequester = remember { FocusRequester() }
    val emptyActionRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (viewModel.displayItems.isEmpty() && !viewModel.isLoading && !viewModel.isEnd) {
            viewModel.refresh(environment)
        }
    }
    LaunchedEffect(viewModel) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }.collect { reachedEnd ->
            if (reachedEnd && !viewModel.isLoading && !viewModel.isEnd) viewModel.loadMore(environment)
        }
    }
    // 首次进入或从详情返回时，把焦点交给第一张卡片或离开前的卡片；滚动位置由 rememberLazyGridState 恢复。
    LaunchedEffect(Unit) {
        val targetKey = restoreFocusKey
        if (!requestInitialFocus && targetKey == null) return@LaunchedEffect
        snapshotFlow { items.isNotEmpty() }.first { it }
        val target = (targetKey?.let { key -> items.indexOfFirst { it.stableKey == key } } ?: 0)
            .takeIf { it >= 0 } ?: 0
        gridState.scrollToItem(target)
        pendingFocusIndex = target
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == target } }.first { it }
        // 等待一帧让目标卡片完成组合并挂上 FocusRequester，否则 requestFocus 会在未初始化时静默失败。
        withFrameNanos { }
        runCatching { pendingFocusRequester.requestFocus() }
        pendingFocusIndex = null
        onInitialFocusRequested()
    }
    // 空信息流也必须给手柄一个焦点目标，否则进入关注/热榜后会停在没有焦点的状态。
    LaunchedEffect(items.isEmpty(), viewModel.isLoading, viewModel.errorMessage) {
        if (items.isEmpty() && !viewModel.isLoading) {
            withFrameNanos { }
            runCatching { emptyActionRequester.requestFocus() }
        }
    }

    TvHints(
        TvHint(TvAction.Up, "移动"),
        TvHint(TvAction.Confirm, "打开"),
        TvHint(TvAction.PrevTab, "上一栏"),
        TvHint(TvAction.NextTab, "下一栏"),
        TvHint(TvAction.Menu, "设置"),
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = kind.title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(24.dp))
            if (viewModel.isLoading) {
                AppLoadingIndicator()
            }
            Spacer(Modifier.weight(1f))
            TvActionPill(text = "搜索", icon = AppIcons.Search, onClick = onOpenSearch)
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = (maxWidth / 500.dp).toInt().coerceIn(2, 4)
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = when {
                            viewModel.errorMessage != null -> "加载失败：${viewModel.errorMessage}"
                            viewModel.isLoading -> "正在加载…"
                            else -> "暂时没有内容"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    TvActionPill(
                        text = "刷新",
                        icon = AppIcons.Refresh,
                        focusRequester = emptyActionRequester,
                        onClick = { viewModel.refresh(environment) },
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.stableKey }) { index, item ->
                        TvFeedCard(
                            item = item,
                            focusRequester = if (index == pendingFocusIndex) pendingFocusRequester else null,
                            onClick = {
                                val destination = item.navDestination
                                if (destination == null) {
                                    userMessages.showShortMessage("手柄模式暂不支持打开该内容")
                                } else {
                                    navigator.onNavigate(destination)
                                }
                            },
                            onFocusChanged = { focused ->
                                if (focused && pendingFocusIndex == null) restoreFocusKey = item.stableKey
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFeedCard(
    item: FeedDisplayItem,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val thumbnail = (item.feed?.target as? Feed.AnswerTarget)?.thumbnail
    TvFocusable(
        onClick = onClick,
        focusRequester = focusRequester,
        onFocusChanged = onFocusChanged,
        shape = cardShape,
        modifier = Modifier.fillMaxWidth(),
    ) { scope ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .tvFocusSurface(
                    scope,
                    if (scope.focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        icon = AppIcons.Newspaper,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        val author = item.authorName.orEmpty()
                        if (author.isNotBlank()) append(author).append(" · ")
                        append(item.details)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
