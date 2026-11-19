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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.WebLoginPane
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.components.TvHints
import com.github.zly2006.zhihu.tv.input.TvAction

/**
 * TV 网页登录页。
 *
 * 原生扫码接口在部分网络环境下会失败，这里直接复用普通界面的网页登录：页面内的二维码由知乎网页端生成，
 * 手机扫码后回调登录成功。手柄只需要能返回，二维码本身用手机扫。
 */
@Composable
fun TvLoginScreen() {
    val navigator = LocalNavigator.current
    TvHints(TvHint(TvAction.Back, "返回"))

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "网页登录",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "用手机知乎扫描页面中的二维码",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .widthIn(max = 900.dp),
        ) {
            WebLoginPane(onLoginSuccess = { navigator.onNavigateBack() })
        }
    }
}
