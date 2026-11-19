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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.navigation.FollowUserUpdates
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.tv.components.TvActionPill
import com.github.zly2006.zhihu.tv.focus.TvFocusable
import com.github.zly2006.zhihu.tv.focus.tvFocusSurface
import com.github.zly2006.zhihu.ui.rememberAccountSettingsAccountState
import com.github.zly2006.zhihu.viewmodel.feed.FollowRecommendViewModel
import com.github.zly2006.zhihu.viewmodel.feed.RecentMomentsViewModel
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import org.koin.compose.koinInject

/** TV 的关注页：推荐 / 动态两个子栏，推荐顶部保留最近动态账号入口。 */
@Composable
fun TvFollowScreen() {
    val account = rememberAccountSettingsAccountState().value
    val navigator = LocalNavigator.current
    if (!account.login) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("登录后查看关注动态", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            TvActionPill("登录", icon = AppIcons.Login, onClick = { navigator.onNavigate(Login) })
        }
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabRequesters = remember { listOf(FocusRequester(), FocusRequester()) }
    val settings = koinInject<SettingsStore>()
    val recommendViewModel: FollowRecommendViewModel = viewModel { FollowRecommendViewModel(settings) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { tabRequesters[0].requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("关注", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(32.dp))
            TvFollowTab("推荐", selectedTab == 0, tabRequesters[0]) { selectedTab = 0 }
            Spacer(Modifier.width(12.dp))
            TvFollowTab("动态", selectedTab == 1, tabRequesters[1]) { selectedTab = 1 }
        }
        if (selectedTab == 0) {
            TvFeedScreen(
                kind = TvFeedKind.Follow,
                onOpenSearch = {},
                requestInitialFocus = false,
                onInitialFocusRequested = {},
                showHeader = false,
                viewModelOverride = recommendViewModel,
                topContent = { TvFollowingUsersRow() },
            )
        } else {
            TvFeedScreen(
                kind = TvFeedKind.Follow,
                onOpenSearch = {},
                requestInitialFocus = false,
                onInitialFocusRequested = {},
                showHeader = false,
            )
        }
    }
}

@Composable
private fun TvFollowTab(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    TvFocusable(
        onClick = onClick,
        focusRequester = focusRequester,
        shape = RoundedCornerShape(20.dp),
    ) { scope ->
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (scope.focused || selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .tvFocusSurface(
                    scope,
                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ).padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

/** 推荐页顶部的最近动态账号；确认键进入该账号的更新列表。 */
@Composable
private fun TvFollowingUsersRow() {
    val navigator = LocalNavigator.current
    val viewModel: RecentMomentsViewModel = viewModel { RecentMomentsViewModel() }
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    LaunchedEffect(Unit) { viewModel.load(environment) }
    if (viewModel.users.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(viewModel.users, key = { it.actor.id }) { user ->
            TvFocusable(
                onClick = {
                    viewModel.markRead(environment, user.actor.id)
                    navigator.onNavigate(
                        FollowUserUpdates(
                            id = user.actor.id,
                            urlToken = user.actor.urlToken,
                            name = user.actor.name,
                        ),
                    )
                },
                shape = RoundedCornerShape(16.dp),
            ) { scope ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .tvFocusSurface(
                            scope,
                            if (scope.focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                        ).padding(8.dp),
                ) {
                    Box {
                        AsyncImage(
                            model = user.actor.avatarUrl,
                            contentDescription = user.actor.name,
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        if (user.unreadCount > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = user.actor.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(72.dp),
                    )
                }
            }
        }
    }
}
