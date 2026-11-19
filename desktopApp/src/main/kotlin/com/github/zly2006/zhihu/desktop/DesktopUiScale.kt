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

package com.github.zly2006.zhihu.desktop

import com.github.zly2006.zhihu.util.Log
import java.util.concurrent.TimeUnit

private const val TAG = "DesktopUiScale"
private const val UI_SCALE_PROPERTY = "sun.java2d.uiScale"

/**
 * 在 AWT / Skiko 初始化前应用 Linux 桌面缩放。
 *
 * X11 下 AWT 不会自动读取 GNOME 的 2x 缩放，导致 Compose 窗口在高分屏上按 1x 渲染。
 * 优先尊重用户显式传入的 `-Dsun.java2d.uiScale`，否则依次读取环境变量、GNOME 缩放与 Xft DPI。
 */
fun applyDesktopUiScale() {
    if (System.getProperty(UI_SCALE_PROPERTY) != null) return
    val scale = detectUiScale() ?: return
    if (scale <= 1.05) return
    System.setProperty(UI_SCALE_PROPERTY, scale.toString())
    Log.i(TAG, "检测到桌面缩放 ${scale}x，已应用到界面")
}

private fun detectUiScale(): Double? {
    environmentScale("GDK_SCALE")?.let { return it }
    environmentScale("QT_SCALE_FACTOR")?.let { return it }
    gnomeScale()?.let { return it }
    xftDpiScale()?.let { return it }
    return null
}

private fun environmentScale(name: String): Double? =
    System
        .getenv(name)
        ?.toDoubleOrNull()
        ?.takeIf { it > 1.05 }
        ?.coerceAtMost(4.0)

private fun gnomeScale(): Double? {
    val output = commandOutput("gsettings", "get", "org.gnome.desktop.interface", "scaling-factor") ?: return null
    // 输出形如 "uint32 2"；取最后一个数字，避免把类型名里的 32 当成缩放值。
    val value = output
        .trim()
        .split(Regex("\\s+"))
        .lastOrNull()
        ?.toDoubleOrNull() ?: return null
    return value.takeIf { it > 1.05 }?.coerceAtMost(4.0)
}

private fun xftDpiScale(): Double? {
    val output = commandOutput("xrdb", "-query") ?: return null
    val dpi = output
        .lineSequence()
        .firstOrNull { it.startsWith("Xft.dpi:") }
        ?.substringAfter(':')
        ?.trim()
        ?.toDoubleOrNull()
        ?: return null
    return (dpi / 96.0).takeIf { it > 1.05 }?.coerceAtMost(4.0)
}

private fun commandOutput(vararg command: String): String? =
    runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        process.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
