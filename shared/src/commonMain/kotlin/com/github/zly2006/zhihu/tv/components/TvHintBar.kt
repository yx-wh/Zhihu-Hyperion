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

package com.github.zly2006.zhihu.tv.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.tv.input.TvAction

/**
 * 底部按键提示条的一格：手柄按键 + 动作说明。
 *
 * [key] 只为组合动作（如「▲▼◀▶ 移动」）覆盖默认按键文字，其余沿用 [TvAction] 的默认标识。
 */
@Immutable
data class TvHint(
    val action: TvAction,
    val label: String,
    val key: String? = null,
) {
    val keyLabel: String
        get() = key ?: action.defaultKeyLabel()
}

fun TvAction.defaultKeyLabel(): String = when (this) {
    TvAction.Up, TvAction.Down, TvAction.Left, TvAction.Right -> "▲▼◀▶"
    TvAction.Confirm -> "A"
    TvAction.Back -> "B"
    TvAction.Menu -> "☰"
    TvAction.PrevTab -> "L1"
    TvAction.NextTab -> "R1"
    TvAction.PreviousItem -> "L2"
    TvAction.NextItem -> "R2"
    TvAction.Secondary -> "X"
}

/** 当前页面声明的按键提示；页面通过 [TvHints] 写入，壳层负责渲染。 */
@Stable
class TvHintState {
    var hints by mutableStateOf<List<TvHint>>(emptyList())
        internal set
}

@Composable
fun rememberTvHintState(): TvHintState = remember { TvHintState() }

val LocalTvHintState = staticCompositionLocalOf<TvHintState> {
    error("TV 模式页面必须在 TvZhihuApp 内部使用")
}

/** 声明当前页面的提示条内容；离开页面时由下一个页面覆盖，不需要手动清理。 */
@Composable
fun TvHints(vararg hints: TvHint) {
    TvHints(hints.toList())
}

@Composable
fun TvHints(hints: List<TvHint>) {
    val state = LocalTvHintState.current
    SideEffect { state.hints = hints }
}

@Composable
fun TvHintBar(
    hints: List<TvHint>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            hints.forEach { hint ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeyCap(hint.keyLabel)
                    Text(
                        text = hint.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 按键帽：统一用描边方块表示手柄按键，不引入额外图标资源。 */
@Composable
private fun KeyCap(label: String) {
    Box(
        modifier = Modifier
            .size(width = if (label.length > 2) 64.dp else 28.dp, height = 28.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
