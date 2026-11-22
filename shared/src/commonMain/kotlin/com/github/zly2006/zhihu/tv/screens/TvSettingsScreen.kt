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

package com.github.zly2006.zhihu.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.tv.TV_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.tv.TV_THEME_MODE_DARK
import com.github.zly2006.zhihu.tv.TV_THEME_MODE_LIGHT
import com.github.zly2006.zhihu.tv.TV_THEME_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.components.TvHints
import com.github.zly2006.zhihu.tv.focus.TvFocusable
import com.github.zly2006.zhihu.tv.focus.tvFocusSurface
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.tv.rememberTvDarkTheme
import com.github.zly2006.zhihu.ui.rememberAccountSettingsAccountState
import com.github.zly2006.zhihu.ui.rememberAppVersionInfo
import org.koin.compose.koinInject

private val settingRowShape = RoundedCornerShape(16.dp)
private val FONT_SIZE_STEPS = listOf(90, 100, 110, 120, 130)

/**
 * 精简的手柄 / TV 设置页。
 *
 * 只放十英尺场景真正需要的项；完整设置仍可在普通界面使用，避免把 106KB 的外观设置页复制一份。
 */
@Composable
fun TvSettingsScreen(
    onExitTvMode: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val settings = koinInject<SettingsStore>()
    val version = rememberAppVersionInfo()
    val account = rememberAccountSettingsAccountState().value
    val fontSize = settings.getInt("contentFontSize", 100)
    val darkTheme = rememberTvDarkTheme()
    val firstRowRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstRowRequester.requestFocus() }
    }

    TvHints(
        TvHint(TvAction.Up, "移动"),
        TvHint(TvAction.Confirm, "确定"),
        TvHint(TvAction.Back, "返回"),
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                TvSettingRow(
                    title = "账号",
                    value = if (account.login) account.username else "未登录",
                    focusRequester = firstRowRequester,
                    onClick = { if (!account.login) onOpenLogin() },
                    // 只展示：未登录时点开登录由 onClick 处理，已登录时不响应。
                )
            }
            item {
                TvSettingRow(
                    title = "主题",
                    value = if (darkTheme) "暗色" else "亮色",
                    onClick = {
                        settings.putString(
                            TV_THEME_MODE_PREFERENCE_KEY,
                            if (darkTheme) TV_THEME_MODE_LIGHT else TV_THEME_MODE_DARK,
                        )
                    },
                )
            }
            item {
                TvSettingRow(
                    title = "正文字号",
                    value = "$fontSize%",
                    onClick = {
                        val next = FONT_SIZE_STEPS.firstOrNull { it > fontSize } ?: FONT_SIZE_STEPS.first()
                        settings.putInt("contentFontSize", next)
                    },
                )
            }
            item {
                TvSettingRow(
                    title = "退出 TV 模式",
                    value = "切回普通界面",
                    onClick = {
                        settings.putBoolean(TV_MODE_PREFERENCE_KEY, false)
                        onExitTvMode()
                    },
                )
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)) {
                    Text(
                        text = "关于",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Zhihu-Hyperion $version",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        focusRequester = focusRequester,
        shape = settingRowShape,
        modifier = Modifier.fillMaxWidth(),
    ) { scope ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(settingRowShape)
                .tvFocusSurface(
                    scope,
                    if (scope.focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                ).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
