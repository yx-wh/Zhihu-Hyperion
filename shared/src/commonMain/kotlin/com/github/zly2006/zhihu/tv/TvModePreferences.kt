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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.rememberIsTelevisionUi
import com.github.zly2006.zhihu.ui.components.rememberObservedSetting
import org.koin.compose.koinInject

/** 手柄 / TV 模式开关；设置页写入，两个宿主读取，切换后立即换界面。 */
const val TV_MODE_PREFERENCE_KEY = "tvMode"

/** TV 模式独立的明暗主题；默认深色，不跟随普通界面的主题设置。 */
const val TV_THEME_MODE_PREFERENCE_KEY = "tvThemeMode"
const val TV_THEME_MODE_DARK = "dark"
const val TV_THEME_MODE_LIGHT = "light"

/**
 * 读取手柄 / TV 模式开关。
 *
 * 默认值由设备形态决定：Android 电视与电视盒子默认进入 TV 界面，手机、平板与桌面默认保持原界面。
 * 使用 [rememberObservedSetting] 以响应设置页的即时切换。
 */
@Composable
fun rememberTvModeEnabled(): Boolean {
    val settings = koinInject<SettingsStore>()
    val televisionUi = rememberIsTelevisionUi()
    val enabled by rememberObservedSetting(settings, TV_MODE_PREFERENCE_KEY) {
        getBoolean(TV_MODE_PREFERENCE_KEY, televisionUi)
    }
    return enabled
}

/** TV 模式是否使用深色主题；设置页切换后立即重组。 */
@Composable
fun rememberTvDarkTheme(): Boolean {
    val settings = koinInject<SettingsStore>()
    val mode by rememberObservedSetting(settings, TV_THEME_MODE_PREFERENCE_KEY) {
        getString(TV_THEME_MODE_PREFERENCE_KEY, TV_THEME_MODE_DARK)
    }
    return mode != TV_THEME_MODE_LIGHT
}
