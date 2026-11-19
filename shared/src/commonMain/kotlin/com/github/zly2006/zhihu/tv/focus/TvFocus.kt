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

package com.github.zly2006.zhihu.tv.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.tv.input.toTvActionOrNull

/**
 * TV 模式的输入上下文。
 *
 * 只记录「当前是否有文本输入框持有焦点」：方向键在文本输入时需要交给输入框移动光标，而不是被根节点拿去做焦点导航。
 */
@Stable
class TvInputState {
    var textEntryActive by mutableStateOf(false)
        internal set
}

@Composable
fun rememberTvInputState(): TvInputState = remember { TvInputState() }

/** 壳层提供的输入上下文；文本输入控件据此暂停根节点的方向键导航。 */
val LocalTvInputState = staticCompositionLocalOf<TvInputState> {
    error("文本输入控件必须在 TvZhihuApp 内部使用")
}

private class TvFocusedActions(
    val owner: Any,
    val onClick: () -> Unit,
    val onSecondary: (() -> Unit)?,
)

/**
 * 当前焦点项的动作登记表。
 *
 * 手柄的确认 / 次要键不经过 Compose 按键管线，无法像键盘那样由焦点项自己的 onKeyEvent 处理；
 * 由根节点把动作转发给最近获得焦点的 [TvFocusable]，避免确认键落到壳层后无人响应。
 */
@Stable
class TvFocusedActionHost {
    private var current: TvFocusedActions? = null
    private var scrollTarget: Pair<Any, (TvAction) -> Boolean>? = null
    private var contentActionTarget: Pair<Any, (TvAction) -> Boolean>? = null

    /** 阅读页登记 L2 / R2 的上一篇 / 下一篇；只有存在可切换内容时返回 true。 */
    fun registerContentAction(
        owner: Any,
        onAction: (TvAction) -> Boolean,
    ): () -> Unit {
        val entry = owner to onAction
        contentActionTarget = entry
        return { if (contentActionTarget === entry) contentActionTarget = null }
    }

    fun performContentAction(action: TvAction): Boolean = contentActionTarget?.second?.invoke(action) == true

    /** 可滚动正文登记方向键处理，优先于焦点移动，避免方向键被根节点吞掉。 */
    fun registerScroll(
        owner: Any,
        onScroll: (TvAction) -> Boolean,
    ): () -> Unit {
        val entry = owner to onScroll
        scrollTarget = entry
        return { if (scrollTarget === entry) scrollTarget = null }
    }

    fun performScroll(action: TvAction): Boolean = scrollTarget?.second?.invoke(action) == true

    fun register(
        owner: Any,
        onClick: () -> Unit,
        onSecondary: (() -> Unit)?,
    ): () -> Unit {
        val entry = TvFocusedActions(owner, onClick, onSecondary)
        current = entry
        return { if (current === entry) current = null }
    }

    fun performConfirm(): Boolean {
        val actions = current ?: return false
        actions.onClick()
        return true
    }

    fun performSecondary(): Boolean {
        val actions = current?.onSecondary ?: return false
        actions()
        return true
    }
}

/** 由 [com.github.zly2006.zhihu.tv.input.TvInputRoot] 提供；TV 页面内的焦点项自动登记。 */
val LocalTvFocusedActionHost = staticCompositionLocalOf<TvFocusedActionHost> {
    error("焦点项必须在 TvInputRoot 内部使用")
}

/**
 * 标记一个文本输入控件：获得焦点时暂停根节点的方向键焦点导航。
 */
@Composable
fun Modifier.tvTextEntry(): Modifier {
    val inputState = LocalTvInputState.current
    DisposableEffect(inputState) {
        onDispose { inputState.textEntryActive = false }
    }
    return onFocusChanged { inputState.textEntryActive = it.isFocused }
}

/** [TvFocusable] 的内容作用域，用于让卡片按焦点态调整配色，不必把焦点状态复制到页面层。 */
@Stable
interface TvFocusableScope {
    val focused: Boolean
}

/** 焦点项内部使用的选中底色，供卡片在焦点态与普通态之间切换。 */
fun Modifier.tvFocusSurface(scope: TvFocusableScope, color: Color): Modifier =
    if (scope.focused) background(color) else this

private class FocusedState(
    override val focused: Boolean,
) : TvFocusableScope

/**
 * 手柄 / TV 模式的焦点项。
 *
 * 方向移动由 TV 根节点统一处理；这里只负责：成为 Compose 焦点目标、绘制焦点环、在焦点到达时滚动可见，
 * 以及处理确认 / 次要动作。按键处理放在焦点项自身，文本输入框等原生可聚焦控件才能先消费自己的按键。
 */
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSecondary: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    scaleOnFocus: Boolean = true,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    content: @Composable (TvFocusableScope) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val highlightColor = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(
        targetValue = if (focused && scaleOnFocus) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 900f),
        label = "tvFocusScale",
    )
    // 按住不放时桌面键盘会重复发送 KeyDown，用状态位保证一次按下只触发一次确认。
    var confirmPressed by remember { mutableStateOf(false) }
    var secondaryPressed by remember { mutableStateOf(false) }

    // 手柄确认键不经过 Compose 按键管线，需要把当前焦点项的动作登记给 TvInputRoot。
    val actionHost = LocalTvFocusedActionHost.current
    val actionOwner = remember { Any() }
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnSecondary by rememberUpdatedState(onSecondary)
    DisposableEffect(focused, actionHost, actionOwner) {
        if (!focused) return@DisposableEffect onDispose {}
        val dispose = actionHost.register(actionOwner, { latestOnClick() }, latestOnSecondary?.let { { it() } })
        onDispose(dispose)
    }

    LaunchedEffect(focused) {
        if (focused) bringIntoViewRequester.bringIntoView()
        onFocusChanged?.invoke(focused)
    }

    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bringIntoViewRequester(bringIntoViewRequester)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.shadow(elevation = if (focused) 16.dp else 0.dp, shape = shape, clip = false)
            .border(
                width = 3.dp,
                color = if (focused) highlightColor else Color.Transparent,
                shape = shape,
            ).focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                when (event.type) {
                    KeyEventType.KeyDown -> when (event.toTvActionOrNull()) {
                        TvAction.Confirm -> {
                            if (!confirmPressed) {
                                confirmPressed = true
                                onClick()
                            }
                            true
                        }

                        TvAction.Secondary -> {
                            val action = onSecondary
                            if (action != null && !secondaryPressed) {
                                secondaryPressed = true
                                action()
                            }
                            action != null
                        }

                        else -> {
                            false
                        }
                    }

                    KeyEventType.KeyUp -> when (event.toTvActionOrNull()) {
                        TvAction.Confirm -> {
                            confirmPressed = false
                            true
                        }

                        TvAction.Secondary -> {
                            secondaryPressed = false
                            onSecondary != null
                        }

                        else -> {
                            false
                        }
                    }

                    else -> false
                }
            },
    ) {
        content(FocusedState(focused))
    }
}

/** 焦点环以外的轻量强调色，用于标题等需要随焦点变化的元素。 */
fun TvFocusableScope.focusContentColor(
    normal: Color,
    highlighted: Color,
): Color = if (focused) highlighted else normal

/** TV 模式下需要显式请求初始焦点时使用，避免每个页面重复写 LaunchedEffect。 */
@Composable
fun RequestInitialFocus(focusRequester: FocusRequester) {
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}
