/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Co-author: eltavine <me@eltavine.com>
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

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.zly2006.zhihu.desktopZhihuModules
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.SnackbarUserMessageHost
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.tv.TV_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.DesktopZhihuMain
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

private const val TV_MODE_ARGUMENT = "--tv"

/**
 * 桌面入口。
 *
 * `--tv` 让 HTPC / 接手柄的场景在启动时直接进入手柄模式，不必先打开普通界面到设置里切换。
 */
fun main(args: Array<String>) {
    applyDesktopUiScale()
    System.setProperty("java.awt.im.style", "below-the-spot")
    startKoin { modules(desktopZhihuModules()) }
    if (TV_MODE_ARGUMENT in args) {
        KoinPlatform.getKoin().get<SettingsStore>().putBoolean(TV_MODE_PREFERENCE_KEY, true)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Zhihu-Hyperion",
            icon = painterResource("desktop-icon.png"),
        ) {
            ZhihuTheme {
                SnackbarUserMessageHost {
                    DesktopZhihuMain()
                }
            }
        }
    }
}
