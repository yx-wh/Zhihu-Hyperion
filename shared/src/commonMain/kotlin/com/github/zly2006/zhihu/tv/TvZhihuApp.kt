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

package com.github.zly2006.zhihu.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.zly2006.zhihu.icons.AppIcon
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.Collections
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.tv.components.TvHintBar
import com.github.zly2006.zhihu.tv.components.TvRailEntry
import com.github.zly2006.zhihu.tv.components.TvSideRail
import com.github.zly2006.zhihu.tv.components.rememberTvHintState
import com.github.zly2006.zhihu.tv.focus.LocalTvInputState
import com.github.zly2006.zhihu.tv.focus.rememberTvInputState
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.tv.input.TvInputRoot
import com.github.zly2006.zhihu.tv.screens.TvFeedKind
import com.github.zly2006.zhihu.tv.screens.TvFeedScreen
import com.github.zly2006.zhihu.tv.screens.TvFollowScreen
import com.github.zly2006.zhihu.ui.rememberAccountSettingsAccountState
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private enum class TvTab(
    val title: String,
    val icon: AppIcon,
) {
    Home("推荐", AppIcons.Home),
    Follow("关注", AppIcons.Group),
    Hot("热榜", AppIcons.Whatshot),
}

/**
 * 手柄 / TV 模式的应用外壳。
 *
 * 与手机界面并列存在：左侧栏目栏、右侧页面栈、底部按键提示条。所有页面只消费 [TvAction]，
 * 输入设备差异（键盘、遥控器、手柄）由 [TvInputRoot] 与平台层消化。
 *
 * @param gamepadActions 桌面手柄轮询得到的动作流；Android 手柄按键走 Compose 按键，不需要这个通道。
 * @param onExitTvMode 用户从设置里退出 TV 模式时回调宿主切回普通界面。
 */
@Composable
fun TvZhihuApp(
    gamepadActions: Flow<TvAction>? = null,
    onExitTvMode: () -> Unit = {},
) {
    ZhihuTheme(darkThemeOverride = rememberTvDarkTheme()) {
        val navController = rememberNavController()
        val inputState = rememberTvInputState()
        val hintState = rememberTvHintState()
        val account = rememberAccountSettingsAccountState().value
        var selectedTabName by rememberSaveable { mutableStateOf(TvTab.Home.name) }
        var initialFocusPending by remember { mutableStateOf(true) }
        val selectedTab = TvTab.entries.firstOrNull { it.name == selectedTabName } ?: TvTab.Home
        val currentEntry by navController.currentBackStackEntryAsState()
        val destination = currentEntry?.destination

        val tabFocusRequesters = remember { TvTab.entries.associateWith { FocusRequester() } }
        var pendingTabFocus by remember { mutableStateOf<TvTab?>(null) }
        var lastBackMark by remember { mutableStateOf<TimeMark?>(null) }
        val actionFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

        // 切栏后内容与侧栏会重组，必须等新焦点项挂上 requester 再请求，否则会静默失败导致整个界面无焦点。
        LaunchedEffect(pendingTabFocus) {
            val tab = pendingTabFocus ?: return@LaunchedEffect
            repeat(3) {
                withFrameNanos { }
                if (runCatching { tabFocusRequesters[tab]?.requestFocus() }.isSuccess) {
                    pendingTabFocus = null
                    return@LaunchedEffect
                }
            }
            pendingTabFocus = null
        }

        fun focusRequesterFor(id: String): FocusRequester = actionFocusRequesters.getOrPut(id) { FocusRequester() }

        fun isAtRoot(): Boolean =
            navController.currentDestination?.id == navController.graph.startDestinationId

        fun popToRoot(): Boolean {
            // 按 graph 的 startDestination id 回退，避免 typed route 查找失败后留下一个不在根页面的返回栈。
            if (isAtRoot()) return true
            return navController.popBackStack(navController.graph.startDestinationId, inclusive = false)
        }

        fun openRoute(route: NavDestination) {
            popToRoot()
            navController.navigate(route) { launchSingleTop = true }
        }

        fun openSettings() {
            popToRoot()
            navController.navigate(TvSettingsRoute) { launchSingleTop = true }
        }

        fun selectTab(tab: TvTab) {
            selectedTabName = tab.name
            popToRoot()
        }

        fun handleAction(action: TvAction): Boolean {
            return when (action) {
                TvAction.Back -> {
                    // 手柄和键盘可能同时上报同一次返回；300ms 内只允许弹一层，避免从详情直接跳过搜索页。
                    val previous = lastBackMark
                    if (previous != null && previous.elapsedNow() < 300.milliseconds) return true
                    lastBackMark = TimeSource.Monotonic.markNow()
                    navController.popBackStack()
                }

                TvAction.Menu -> {
                    openSettings()
                    true
                }

                TvAction.PrevTab, TvAction.NextTab -> {
                    // 只在栏目根页面切栏，避免阅读中途误触把用户带回首页。
                    if (!isAtRoot()) return false
                    // 手柄动作流可能持有较早的回调，这里从 state 重新取当前栏目，避免连续切栏只切一次。
                    val currentTab = TvTab.entries.firstOrNull { it.name == selectedTabName } ?: TvTab.Home
                    val delta = if (action == TvAction.PrevTab) -1 else 1
                    val next = TvTab.entries[(currentTab.ordinal + delta + TvTab.entries.size) % TvTab.entries.size]
                    selectTab(next)
                    pendingTabFocus = next
                    true
                }

                else -> {
                    false
                }
            }
        }

        TvInputRoot(
            inputState = inputState,
            onAction = ::handleAction,
            externalActions = gamepadActions,
        ) {
            CompositionLocalProvider(
                LocalTvInputState provides inputState,
                com.github.zly2006.zhihu.tv.components.LocalTvHintState provides hintState,
                LocalNavigator provides Navigator(
                    onNavigate = { route -> navController.navigate(route) },
                    onNavigateBack = { navController.popBackStack() },
                ),
            ) {
                Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val onRoot = destination?.id == navController.graph.startDestinationId
                    TvSideRail(
                        entries = buildList {
                            TvTab.entries.forEach { tab ->
                                add(
                                    TvRailEntry(
                                        id = tab.name,
                                        label = tab.title,
                                        icon = tab.icon,
                                        selected = onRoot && selectedTab == tab,
                                        onClick = { selectTab(tab) },
                                        focusRequester = tabFocusRequesters[tab],
                                    ),
                                )
                            }
                            add(
                                TvRailEntry(
                                    id = "search",
                                    label = "搜索",
                                    icon = AppIcons.Search,
                                    selected = destination?.hasRoute<Search>() == true,
                                    onClick = { openRoute(Search()) },
                                    focusRequester = focusRequesterFor("search"),
                                ),
                            )
                            add(
                                TvRailEntry(
                                    id = "collections",
                                    label = "收藏",
                                    icon = AppIcons.Bookmarks,
                                    selected = destination?.hasRoute<Collections>() == true ||
                                        destination?.hasRoute<CollectionContent>() == true,
                                    onClick = {
                                        if (account.login) {
                                            openRoute(Collections(account.urlToken.orEmpty()))
                                        } else {
                                            openRoute(Login)
                                        }
                                    },
                                    focusRequester = focusRequesterFor("collections"),
                                ),
                            )
                            add(
                                TvRailEntry(
                                    id = "history",
                                    label = "历史",
                                    icon = AppIcons.History,
                                    selected = destination?.hasRoute<TvHistoryRoute>() == true,
                                    onClick = {
                                        popToRoot()
                                        navController.navigate(TvHistoryRoute) { launchSingleTop = true }
                                    },
                                    focusRequester = focusRequesterFor("history"),
                                ),
                            )
                            add(
                                TvRailEntry(
                                    id = "notification",
                                    label = "通知",
                                    icon = AppIcons.Notifications,
                                    selected = destination?.hasRoute<Notification>() == true,
                                    onClick = { openRoute(Notification) },
                                    focusRequester = focusRequesterFor("notification"),
                                ),
                            )
                            if (!account.login) {
                                add(
                                    TvRailEntry(
                                        id = "login",
                                        label = "登录",
                                        icon = AppIcons.Person,
                                        selected = destination?.hasRoute<Login>() == true,
                                        onClick = { openRoute(Login) },
                                        focusRequester = focusRequesterFor("login"),
                                    ),
                                )
                            }
                            add(
                                TvRailEntry(
                                    id = "settings",
                                    label = "设置",
                                    icon = AppIcons.Settings,
                                    selected = destination?.hasRoute<TvSettingsRoute>() == true,
                                    onClick = ::openSettings,
                                    focusRequester = focusRequesterFor("settings"),
                                ),
                            )
                        },
                    )
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.weight(1f)) {
                            TvNavHost(
                                navController = navController,
                                onExitTvMode = onExitTvMode,
                                onOpenLogin = { openRoute(Login) },
                                rootContent = {
                                    when (selectedTab) {
                                        TvTab.Follow -> {
                                            TvFollowScreen()
                                        }

                                        TvTab.Home, TvTab.Hot -> {
                                            val kind = when (selectedTab) {
                                                TvTab.Home -> TvFeedKind.Home
                                                TvTab.Hot -> TvFeedKind.Hot
                                                else -> error("unreachable")
                                            }
                                            TvFeedScreen(
                                                kind = kind,
                                                onOpenSearch = { openRoute(Search()) },
                                                requestInitialFocus = initialFocusPending,
                                                onInitialFocusRequested = { initialFocusPending = false },
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        TvHintBar(hints = hintState.hints)
                    }
                }
            }
        }
    }
}
