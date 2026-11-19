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

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/**
 * 手柄 / TV 模式内部唯一的输入语义。
 *
 * Android 的手柄与遥控器按键天然以 Compose [KeyEvent] 到达，桌面键盘走同一映射，Desktop 手柄轮询也归一到
 * 这套动作；页面只消费动作，不再区分输入设备，避免出现第二套「手柄专用」交互。
 */
enum class TvAction {
    Up,
    Down,
    Left,
    Right,
    Confirm,
    Back,
    Menu,
    PrevTab,
    NextTab,

    /** X / Y 等肩键外的次要动作，例如阅读页的赞同。 */
    Secondary,
    ;

    val isDirection: Boolean
        get() = this == Up || this == Down || this == Left || this == Right
}

/** 方向动作用于移动焦点；非方向动作返回 null。 */
fun TvAction.toFocusDirectionOrNull(): FocusDirection? = when (this) {
    TvAction.Up -> FocusDirection.Up
    TvAction.Down -> FocusDirection.Down
    TvAction.Left -> FocusDirection.Left
    TvAction.Right -> FocusDirection.Right
    else -> null
}

/**
 * 把 Compose 按键映射成 [TvAction]。
 *
 * 这里只做设备无关的键位归一：键盘方向键、PageUp/PageDown 与手柄方向键、肩键、ABXY 都落到同一动作，
 * 调用方需按 [androidx.compose.ui.input.key.KeyEventType] 自行决定是否响应按下沿。
 */
fun KeyEvent.toTvActionOrNull(): TvAction? = when (key) {
    Key.DirectionUp, Key.NumPadDirectionUp -> TvAction.Up
    Key.DirectionDown, Key.NumPadDirectionDown -> TvAction.Down
    Key.DirectionLeft, Key.NumPadDirectionLeft -> TvAction.Left
    Key.DirectionRight, Key.NumPadDirectionRight -> TvAction.Right
    Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar, Key.ButtonA -> TvAction.Confirm
    Key.Escape, Key.ButtonB -> TvAction.Back
    Key.Menu, Key.ButtonStart -> TvAction.Menu
    Key.PageUp, Key.NumPadPageUp, Key.ButtonL1, Key.ButtonL2 -> TvAction.PrevTab
    Key.PageDown, Key.NumPadPageDown, Key.ButtonR1, Key.ButtonR2 -> TvAction.NextTab
    Key.ButtonX, Key.ButtonY -> TvAction.Secondary
    else -> null
}
