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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.components.TvHints
import com.github.zly2006.zhihu.tv.focus.TvFocusable
import com.github.zly2006.zhihu.tv.focus.tvFocusSurface
import kotlinx.coroutines.flow.first

private val listRowShape = RoundedCornerShape(16.dp)

/**
 * TV 二级列表页的共用骨架：标题、空状态、分页触发和首项焦点。
 *
 * 收藏、历史、通知和收藏内容只有数据源与行内容不同，容器行为保持一处，避免每个页面各写一套焦点与加载逻辑。
 */
@Composable
fun <T> TvListScreen(
    title: String,
    items: List<T>,
    itemKey: (T) -> String,
    emptyText: String,
    hints: List<TvHint>,
    modifier: Modifier = Modifier,
    onLoadMore: (() -> Unit)? = null,
    rowContent: @Composable (index: Int, item: T, focusRequester: FocusRequester?, onFocusChanged: (Boolean) -> Unit) -> Unit,
) {
    val listState = rememberLazyListState()
    val pendingFocusRequester = remember { FocusRequester() }
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }
    var restoreFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)

    TvHints(hints)
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isEmpty()) return@LaunchedEffect
        val target = restoreFocusKey
            ?.let { key -> items.indexOfFirst { itemKey(it) == key } }
            ?.takeIf { it >= 0 } ?: 0
        listState.scrollToItem(target)
        pendingFocusIndex = target
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == target } }.first { it }
        withFrameNanos { }
        runCatching { pendingFocusRequester.requestFocus() }
        pendingFocusIndex = null
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }.collect { reachedEnd ->
            if (reachedEnd) latestOnLoadMore?.invoke()
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement
                    .spacedBy(8.dp),
            ) {
                itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
                    rowContent(
                        index,
                        item,
                        if (index == pendingFocusIndex) pendingFocusRequester else null,
                        { focused -> if (focused) restoreFocusKey = itemKey(item) },
                    )
                }
            }
        }
    }
}

/** 二级列表的统一行样式：标题 + 次级信息，焦点态使用更高的容器色。 */
@Composable
fun TvListRow(
    title: String,
    subtitle: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    trailing: String? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    TvFocusable(
        onClick = onClick,
        focusRequester = focusRequester,
        onFocusChanged = onFocusChanged,
        shape = listRowShape,
        modifier = Modifier.fillMaxWidth(),
    ) { scope ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(listRowShape)
                .tvFocusSurface(
                    scope,
                    if (scope.focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                ).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!trailing.isNullOrBlank()) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
