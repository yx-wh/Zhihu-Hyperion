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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.util.Log
import com.studiohartman.jamepad.Configuration
import com.studiohartman.jamepad.ControllerAxis
import com.studiohartman.jamepad.ControllerButton
import com.studiohartman.jamepad.ControllerIndex
import com.studiohartman.jamepad.ControllerManager
import com.studiohartman.jamepad.ControllerUnpluggedException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

private const val TAG = "TvGamepad"
private const val POLL_INTERVAL_MS = 16L
private const val STICK_ENTER_THRESHOLD = 0.65f
private const val STICK_EXIT_THRESHOLD = 0.3f

private val BUTTON_ACTIONS = listOf(
    ControllerButton.DPAD_UP to TvAction.Up,
    ControllerButton.DPAD_DOWN to TvAction.Down,
    ControllerButton.DPAD_LEFT to TvAction.Left,
    ControllerButton.DPAD_RIGHT to TvAction.Right,
    ControllerButton.A to TvAction.Confirm,
    ControllerButton.B to TvAction.Back,
    ControllerButton.X to TvAction.Secondary,
    ControllerButton.Y to TvAction.Secondary,
    ControllerButton.START to TvAction.Menu,
    ControllerButton.LEFTBUMPER to TvAction.PrevTab,
    ControllerButton.RIGHTBUMPER to TvAction.NextTab,
)

/**
 * Desktop 手柄轮询源。
 *
 * SDL2 的按键与摇杆状态只能轮询，因此放在独立守护线程按约 60Hz 读取；只在按键按下沿发出一次 [TvAction]，
 * 长按不会连续导航。初始化失败（没有手柄、缺少系统库）只记录日志，TV 模式仍可用键盘操作。
 */
class DesktopGamepadSource : AutoCloseable {
    private val running = AtomicBoolean(true)
    private val mutableActions = MutableSharedFlow<TvAction>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<TvAction> = mutableActions.asSharedFlow()

    private val worker = thread(isDaemon = true, name = "zhihu-tv-gamepad") { pollLoop() }

    private fun pollLoop() {
        val manager = try {
            // 仓库不附带 gamecontrollerdb.txt；关闭内存映射表后 SDL 会使用自带映射，
            // 否则 jamepad 每次启动都会打印一次无法打开资源的异常。
            ControllerManager(
                Configuration().apply {
                    loadDatabaseInMemory = false
                },
            ).apply { initSDLGamepad() }
        } catch (t: Throwable) {
            Log.w(TAG, "桌面手柄初始化失败，改为键盘操作", t)
            return
        }
        var connectedCount = -1
        // 每个手柄单独维护摇杆方向：共用一个变量时，另一只手柄居中会把状态清掉，导致按住摇杆时重复发动作。
        val stickDirections = mutableMapOf<Int, TvAction?>()
        try {
            while (running.get()) {
                manager.update()
                // 读取所有已连接手柄，而不是固定 0 号：热插拔会改变索引，同时插多个手柄时也要都能操作。
                val controllers = (0 until manager.getNumControllers())
                    .map(manager::getControllerIndex)
                    .filter { it.isConnected }
                if (controllers.size != connectedCount) {
                    connectedCount = controllers.size
                    Log.i(
                        TAG,
                        if (controllers.isEmpty()) "手柄已断开" else "手柄已连接：${controllers.joinToString { it.readableName() }}",
                    )
                }
                val activeIds = controllers.mapTo(mutableSetOf()) { it.index }
                controllers.forEach { controller ->
                    val id = controller.index
                    controller.dispatchButtonActions()
                    stickDirections[id] = controller.dispatchStickAction(stickDirections[id])
                }
                stickDirections.keys.retainAll(activeIds)
                Thread.sleep(POLL_INTERVAL_MS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            Log.w(TAG, "手柄轮询异常退出", t)
        } finally {
            runCatching { manager.quitSDLGamepad() }
        }
    }

    private fun ControllerIndex.dispatchButtonActions() {
        try {
            BUTTON_ACTIONS.forEach { (button, action) ->
                if (isButtonJustPressed(button)) mutableActions.tryEmit(action)
            }
        } catch (_: ControllerUnpluggedException) {
            // 热插拔瞬间 SDL 会短暂报告未连接，下一帧会重新读取真实状态。
        }
    }

    /** 左摇杆按方向锁存：越过进入阈值触发一次，回到退出阈值以内才允许再次触发。 */
    private fun ControllerIndex.dispatchStickAction(current: TvAction?): TvAction? {
        val x = runCatching { getAxisState(ControllerAxis.LEFTX) }.getOrNull() ?: return null
        val y = runCatching { getAxisState(ControllerAxis.LEFTY) }.getOrNull() ?: return null
        val direction = when {
            y <= -STICK_ENTER_THRESHOLD -> TvAction.Up
            y >= STICK_ENTER_THRESHOLD -> TvAction.Down
            x <= -STICK_ENTER_THRESHOLD -> TvAction.Left
            x >= STICK_ENTER_THRESHOLD -> TvAction.Right
            else -> null
        }
        if (direction != null) {
            if (direction != current) mutableActions.tryEmit(direction)
            return direction
        }
        if (abs(x) < STICK_EXIT_THRESHOLD && abs(y) < STICK_EXIT_THRESHOLD) return null
        return current
    }

    private fun ControllerIndex.readableName(): String =
        runCatching { name }.getOrDefault("未知手柄")

    override fun close() {
        running.set(false)
        worker.interrupt()
        runCatching { worker.join(500) }
    }
}

/**
 * TV 模式开启时在桌面侧启动手柄轮询；关闭或离开组合时停止线程。返回 null 表示当前没有可用手柄源。
 */
@Composable
fun rememberDesktopGamepadActions(): SharedFlow<TvAction>? {
    var actions by remember { mutableStateOf<SharedFlow<TvAction>?>(null) }
    DisposableEffect(Unit) {
        val source = runCatching { DesktopGamepadSource() }.getOrNull()
        actions = source?.actions
        onDispose { source?.close() }
    }
    return actions
}
