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

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.tv.input.toFocusDirectionOrNull
import com.github.zly2006.zhihu.tv.input.toTvActionOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 手柄与键盘必须落到同一套动作语义；这里锁定键位表，避免后续改动悄悄让某个设备的按键失效。
 */
@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
class TvActionMappingTest {
    private fun actionOf(key: Key): TvAction? = KeyEvent(key, KeyEventType.KeyDown).toTvActionOrNull()

    @Test
    fun directionKeysMapToDirections() {
        assertEquals(TvAction.Up, actionOf(Key.DirectionUp))
        assertEquals(TvAction.Up, actionOf(Key.NumPadDirectionUp))
        assertEquals(TvAction.Down, actionOf(Key.DirectionDown))
        assertEquals(TvAction.Down, actionOf(Key.NumPadDirectionDown))
        assertEquals(TvAction.Left, actionOf(Key.DirectionLeft))
        assertEquals(TvAction.Left, actionOf(Key.NumPadDirectionLeft))
        assertEquals(TvAction.Right, actionOf(Key.DirectionRight))
        assertEquals(TvAction.Right, actionOf(Key.NumPadDirectionRight))
    }

    @Test
    fun confirmKeysIncludeKeyboardAndGamepad() {
        listOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar, Key.ButtonA).forEach {
            assertEquals(TvAction.Confirm, actionOf(it), "按键 $it 应映射为确认")
        }
    }

    @Test
    fun backMenuAndTabKeysMapToTheirActions() {
        assertEquals(TvAction.Back, actionOf(Key.Escape))
        assertEquals(TvAction.Back, actionOf(Key.ButtonB))
        assertEquals(TvAction.Menu, actionOf(Key.Menu))
        assertEquals(TvAction.Menu, actionOf(Key.ButtonStart))
        listOf(Key.PageUp, Key.NumPadPageUp, Key.ButtonL1).forEach {
            assertEquals(TvAction.PrevTab, actionOf(it), "按键 $it 应映射为上一栏")
        }
        listOf(Key.PageDown, Key.NumPadPageDown, Key.ButtonR1).forEach {
            assertEquals(TvAction.NextTab, actionOf(it), "按键 $it 应映射为下一栏")
        }
        assertEquals(TvAction.PreviousItem, actionOf(Key.ButtonL2))
        assertEquals(TvAction.NextItem, actionOf(Key.ButtonR2))
    }

    @Test
    fun faceButtonsProvideSecondaryAction() {
        assertEquals(TvAction.Secondary, actionOf(Key.ButtonX))
        assertEquals(TvAction.Secondary, actionOf(Key.ButtonY))
    }

    @Test
    fun unrelatedKeysAreNotClaimed() {
        listOf(Key.A, Key.Tab, Key.Backspace, Key.VolumeUp).forEach { key ->
            assertNull(actionOf(key), "按键 $key 不应被 TV 输入层拦截")
        }
    }

    @Test
    fun onlyDirectionActionsProduceFocusDirections() {
        assertEquals(FocusDirection.Up, TvAction.Up.toFocusDirectionOrNull())
        assertEquals(FocusDirection.Down, TvAction.Down.toFocusDirectionOrNull())
        assertEquals(FocusDirection.Left, TvAction.Left.toFocusDirectionOrNull())
        assertEquals(FocusDirection.Right, TvAction.Right.toFocusDirectionOrNull())
        listOf(
            TvAction.Confirm,
            TvAction.Back,
            TvAction.Menu,
            TvAction.PrevTab,
            TvAction.NextTab,
            TvAction.PreviousItem,
            TvAction.NextItem,
            TvAction.Secondary,
        ).forEach { assertNull(it.toFocusDirectionOrNull(), "$it 不是方向动作") }
    }
}
