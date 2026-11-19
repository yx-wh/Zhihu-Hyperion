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

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.ArticleTypeNavType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.Collections
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.tv.screens.TvCollectionContentScreen
import com.github.zly2006.zhihu.tv.screens.TvCollectionsScreen
import com.github.zly2006.zhihu.tv.screens.TvCommentScreen
import com.github.zly2006.zhihu.tv.screens.TvHistoryScreen
import com.github.zly2006.zhihu.tv.screens.TvLoginScreen
import com.github.zly2006.zhihu.tv.screens.TvNotificationScreen
import com.github.zly2006.zhihu.tv.screens.TvReaderScreen
import com.github.zly2006.zhihu.tv.screens.TvSearchScreen
import com.github.zly2006.zhihu.tv.screens.TvSettingsScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf

/** TV 壳自己的根路由：栏目内容由 [TvZhihuApp] 直接渲染，不占用共享 `NavDestination`。 */
@Serializable
internal data object TvRootRoute

/** 评论页需要携带被评论内容的完整身份，用与信息流相同的多态编码传递。 */
@Serializable
internal data class TvCommentsRoute(
    val contentJson: String,
)

@Serializable
internal data object TvSettingsRoute

/**
 * TV 自己的在线历史路由。
 *
 * 共享的 `OnlineHistory` 只是主壳 pager 的顶层 tab 目标，不是 NavHost route；手机端通过 pager 选中它。
 * TV 模式没有 pager，需要一条独立路由才能压入/弹出返回栈。
 */
@Serializable
internal data object TvHistoryRoute

private val tvRouteJson = Json { ignoreUnknownKeys = true }

internal fun NavDestination.encodeTvRouteJson(): String = tvRouteJson.encodeToString<NavDestination>(this)

internal fun String.decodeTvNavDestination(): NavDestination? =
    runCatching { tvRouteJson.decodeFromString<NavDestination>(this) }.getOrNull()

/**
 * TV 模式的路由表：只注册首期支持的页面。
 *
 * 未注册的目的地（写回答、私信等）不会被打开，壳层会提前给出提示，而不是落到空白页。
 */
@Composable
internal fun TvNavHost(
    navController: NavHostController,
    onExitTvMode: () -> Unit,
    onOpenLogin: () -> Unit,
    rootContent: @Composable () -> Unit,
) {
    fun openComments(content: NavDestination) {
        navController.navigate(TvCommentsRoute(content.encodeTvRouteJson()))
    }

    NavHost(
        navController = navController,
        startDestination = TvRootRoute,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable<TvRootRoute> { rootContent() }
        composable<Article>(typeMap = mapOf(typeOf<ArticleType>() to ArticleTypeNavType)) { entry ->
            TvReaderScreen(entry.toRoute<Article>(), onOpenComments = ::openComments)
        }
        composable<Question> { entry ->
            TvReaderScreen(entry.toRoute<Question>(), onOpenComments = ::openComments)
        }
        composable<Pin> { entry ->
            TvReaderScreen(entry.toRoute<Pin>(), onOpenComments = ::openComments)
        }
        composable<Search> { TvSearchScreen() }
        composable<Login> { TvLoginScreen() }
        composable<Collections> { entry ->
            TvCollectionsScreen(entry.toRoute<Collections>().userToken)
        }
        composable<CollectionContent> { entry ->
            TvCollectionContentScreen(entry.toRoute<CollectionContent>().collectionId)
        }
        composable<TvHistoryRoute> { TvHistoryScreen() }
        composable<Notification> { TvNotificationScreen() }
        composable<TvCommentsRoute> { entry ->
            val content = entry.toRoute<TvCommentsRoute>().contentJson.decodeTvNavDestination()
            if (content == null) {
                TvUnsupportedDestination()
            } else {
                TvCommentScreen(content)
            }
        }
        composable<TvSettingsRoute> {
            TvSettingsScreen(onExitTvMode = onExitTvMode, onOpenLogin = onOpenLogin)
        }
    }
}

/** 目的地无法在手柄模式下重建时的显式降级页，避免出现空白页。 */
@Composable
internal fun TvUnsupportedDestination() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "该功能暂不支持手柄模式",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
