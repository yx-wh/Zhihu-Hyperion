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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.components.TvHints
import com.github.zly2006.zhihu.tv.focus.TvFocusable
import com.github.zly2006.zhihu.tv.focus.tvFocusSurface
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.ui.commentThreadKey
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import org.koin.compose.koinInject

private val commentRowShape = RoundedCornerShape(16.dp)

/**
 * TV 评论页：整行大列表，确认键点赞。
 *
 * 评论数据、排序与点赞协议全部复用 [RootCommentViewModel]；TV 只提供十英尺布局。
 */
@Composable
fun TvCommentScreen(content: NavDestination) {
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val blockedUsers = koinInject<ContentFilterDatabase>().blockedUserDao()
    val threadKey = content.commentThreadKey()
    val viewModel: RootCommentViewModel = viewModel(key = "tv_comments_$threadKey") {
        RootCommentViewModel(content, blockedUsers)
    }
    val listState = rememberLazyListState()
    val firstItemRequester = remember { FocusRequester() }

    LaunchedEffect(threadKey) {
        if (viewModel.errorMessage == null && viewModel.allData.isEmpty()) viewModel.loadMore(environment)
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }.collect { reachedEnd ->
            if (reachedEnd && !viewModel.isLoading && !viewModel.isEnd && viewModel.errorMessage == null) {
                viewModel.loadMore(environment)
            }
        }
    }
    LaunchedEffect(viewModel.allData.isNotEmpty()) {
        if (viewModel.allData.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstItemRequester.requestFocus() }
        }
    }

    TvHints(
        TvHint(TvAction.Up, "移动"),
        TvHint(TvAction.Confirm, "点赞"),
        TvHint(TvAction.Back, "返回"),
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "评论",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        val comments = viewModel.allData
        if (comments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        viewModel.errorMessage != null -> "加载失败：${viewModel.errorMessage}"
                        viewModel.isLoading -> "正在加载…"
                        else -> viewModel.commentClosedMessage ?: "还没有评论"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(comments, key = { _, comment -> comment.id }) { index, comment ->
                    TvCommentRow(
                        comment = comment,
                        focusRequester = if (index == 0) firstItemRequester else null,
                        onToggleLike = {
                            viewModel.toggleLikeComment(commentData = comment, environment = environment) { }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvCommentRow(
    comment: DataHolder.Comment,
    focusRequester: FocusRequester?,
    onToggleLike: () -> Unit,
) {
    // like 状态不在 data class 的可观察字段里，行内保留一份本地状态，成功后立即反映。
    var liked by remember(comment.id, comment.liked) { mutableStateOf(comment.liked) }
    var likeCount by remember(comment.id, comment.likeCount) { mutableIntStateOf(comment.likeCount) }
    val latestToggle by rememberUpdatedState(onToggleLike)
    val text = remember(comment.content) {
        if ('<' in comment.content) Ksoup.parse(comment.content).text().trim() else comment.content
    }

    TvFocusable(
        onClick = {
            latestToggle()
            liked = !liked
            likeCount += if (liked) 1 else -1
        },
        focusRequester = focusRequester,
        shape = commentRowShape,
        modifier = Modifier.fillMaxWidth(),
    ) { scope ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(commentRowShape)
                .tvFocusSurface(
                    scope,
                    if (scope.focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                ).padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (liked) "已赞 $likeCount" else "$likeCount 赞",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (comment.childCommentCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${comment.childCommentCount} 条回复",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
