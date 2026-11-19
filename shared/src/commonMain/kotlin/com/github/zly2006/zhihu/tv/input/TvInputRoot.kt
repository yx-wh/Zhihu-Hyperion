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

package com.github.zly2006.zhihu.tv.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.github.zly2006.zhihu.tv.focus.LocalTvFocusedActionHost
import com.github.zly2006.zhihu.tv.focus.TvFocusedActionHost
import com.github.zly2006.zhihu.tv.focus.TvInputState
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private class ActionThrottle {
    private var lastAction: TvAction? = null
    private var lastMark: TimeMark? = null

    /** 两个手柄可能同时上报同一次按键；120ms 内的同动作只处理一次。 */
    fun accept(action: TvAction): Boolean {
        val mark = TimeSource.Monotonic.markNow()
        val previous = lastMark
        if (action == lastAction && previous != null && previous.elapsedNow() < 120.milliseconds) return false
        lastAction = action
        lastMark = mark
        return true
    }
}

/**
 * TV 模式的根输入。
 *
 * 方向动作统一落到 Compose 焦点系统；找不到可移动目标时不消费按键，让页面自己的滚动容器处理边界滚动。
 * 其余动作（返回、菜单、切栏）交给壳层决定。动作只在按下沿触发，避免手柄/键盘长按造成重复导航。
 *
 * [externalActions] 用于桌面手柄这类不经过 Compose 按键管线的输入源；Android 手柄与遥控器按键本身就以
 * 按键事件到达，不需要额外通道。
 */
@Composable
fun TvInputRoot(
    inputState: TvInputState,
    onAction: (TvAction) -> Boolean,
    modifier: Modifier = Modifier,
    externalActions: Flow<TvAction>? = null,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusedActionHost = remember { TvFocusedActionHost() }
    val actionThrottle = remember { ActionThrottle() }
    // 手柄动作流在 LaunchedEffect 中长期收集，必须读取最新回调，否则会一直用首次组合时的 selectedTab / 返回栈。
    val currentOnAction by rememberUpdatedState(onAction)

    fun moveFocus(action: TvAction): Boolean {
        if (inputState.textEntryActive) return false
        if (focusedActionHost.performScroll(action)) return true
        return focusManager.moveFocus(action.toFocusDirectionOrNull() ?: return false)
    }

    fun performContentAction(action: TvAction): Boolean =
        focusedActionHost.performContentAction(action) || currentOnAction(action)

    fun handle(action: TvAction) {
        when {
            action.isDirection -> moveFocus(action)
            action == TvAction.PreviousItem || action == TvAction.NextItem -> performContentAction(action)
            else -> currentOnAction(action)
        }
    }

    fun handleExternal(action: TvAction) {
        if (!actionThrottle.accept(action)) return
        when (action) {
            TvAction.Confirm -> if (!focusedActionHost.performConfirm()) currentOnAction(action)
            TvAction.Secondary -> if (!focusedActionHost.performSecondary()) currentOnAction(action)
            else -> handle(action)
        }
    }

    LaunchedEffect(externalActions, focusedActionHost) {
        externalActions?.collect(::handleExternal)
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val action = event.toTvActionOrNull() ?: return@onPreviewKeyEvent false
                val direction = action.toFocusDirectionOrNull()
                if (direction != null) {
                    // 文本输入时方向键属于光标移动，不能被根节点抢走；正文滚动优先于焦点移动。
                    moveFocus(action)
                } else if (action == TvAction.PreviousItem || action == TvAction.NextItem) {
                    performContentAction(action)
                } else {
                    currentOnAction(action)
                }
            },
    ) {
        CompositionLocalProvider(LocalTvFocusedActionHost provides focusedActionHost) {
            content()
        }
    }
}
