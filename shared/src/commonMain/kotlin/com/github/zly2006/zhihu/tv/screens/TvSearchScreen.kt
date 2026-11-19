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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.tv.components.TvActionPill
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.components.TvHints
import com.github.zly2006.zhihu.tv.focus.TvFocusable
import com.github.zly2006.zhihu.tv.focus.tvFocusSurface
import com.github.zly2006.zhihu.tv.focus.tvTextEntry
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.ui.HotSearchItem
import com.github.zly2006.zhihu.ui.fetchHotSearchItems
import com.github.zly2006.zhihu.ui.loadSearchHistory
import com.github.zly2006.zhihu.ui.saveSearchHistory
import com.github.zly2006.zhihu.viewmodel.feed.SearchEntity
import com.github.zly2006.zhihu.viewmodel.feed.SearchViewModel
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import org.koin.compose.koinInject

private val rowShape = RoundedCornerShape(16.dp)

/**
 * 手柄 / TV 模式的搜索页。
 *
 * 搜索框用页面内浮层承载，避免 Dialog 建立独立焦点树导致手柄按键进不去；热搜与历史复用手机端同一套
 * 请求和历史读写。
 */
@Composable
fun TvSearchScreen() {
    val settings = koinInject<SettingsStore>()
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val blockedUsers = koinInject<ContentFilterDatabase>().blockedUserDao()
    val navigator = LocalNavigator.current
    val keyboard = LocalSoftwareKeyboardController.current

    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var showInput by rememberSaveable { mutableStateOf(false) }
    var hotSearches by remember { mutableStateOf<List<HotSearchItem>>(emptyList()) }
    val historyItems = remember(settings) { loadSearchHistory(settings) }

    val fieldFocusRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }

    val viewModel: SearchViewModel? = if (submittedQuery.isBlank()) {
        null
    } else {
        viewModel(key = "tv_search_$submittedQuery") {
            SearchViewModel(submittedQuery, settings, blockedUsers)
        }
    }
    LaunchedEffect(submittedQuery) {
        if (viewModel != null && viewModel.entities.isEmpty() && !viewModel.isLoading) {
            viewModel.refresh(environment)
        }
    }
    LaunchedEffect(Unit) {
        hotSearches = fetchHotSearchItems(environment)
    }
    LaunchedEffect(showInput) {
        if (showInput) {
            withFrameNanos { }
            runCatching { fieldFocusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    fun submit(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        submittedQuery = trimmed
        showInput = false
        keyboard?.hide()
        if (settings.getBoolean("showSearchHistory", true)) {
            saveSearchHistory(settings, (listOf(trimmed) + historyItems.filterNot { it == trimmed }).take(20))
        }
    }

    val results = viewModel?.entities.orEmpty()
    LaunchedEffect(submittedQuery, results.size, showInput) {
        if (!showInput && results.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstResultRequester.requestFocus() }
        }
    }

    TvHints(
        TvHint(TvAction.Confirm, if (showInput) "搜索" else "打开"),
        TvHint(TvAction.Back, "返回"),
        TvHint(TvAction.Up, "移动"),
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(24.dp))
                TvActionPill(
                    text = submittedQuery.ifBlank { "输入关键词" },
                    icon = AppIcons.Search,
                    onClick = { showInput = true },
                )
            }

            when {
                submittedQuery.isBlank() -> {
                    LazyColumn(
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (settings.getBoolean("showSearchHotSearch", true) && hotSearches.isNotEmpty()) {
                            item { TvSectionTitle("热搜") }
                            items(hotSearches, key = { "hot_${it.query}" }) { item ->
                                TvSuggestionRow(text = item.query, onClick = { submit(item.query) })
                            }
                        }
                        if (settings.getBoolean("showSearchHistory", true) && historyItems.isNotEmpty()) {
                            item { TvSectionTitle("搜索历史") }
                            items(historyItems, key = { "history_$it" }) { item ->
                                TvSuggestionRow(text = item, onClick = { submit(item) })
                            }
                        }
                    }
                }

                results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (viewModel?.isLoading == true) "正在搜索…" else "没有找到结果",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(results, key = { _, entity -> entity.id }) { index, entity ->
                            TvSearchResultRow(
                                entity = entity,
                                focusRequester = if (index == 0) firstResultRequester else null,
                                onClick = {
                                    when (entity) {
                                        is SearchEntity.Content -> entity.item.navDestination?.let { navigator.onNavigate(it) }

                                        is SearchEntity.Person -> navigator.onNavigate(
                                            Person(
                                                id = entity.person.id,
                                                urlToken = entity.person.urlToken.orEmpty(),
                                                name = entity.person.name,
                                            ),
                                        )

                                        is SearchEntity.Topic -> navigator.onNavigate(
                                            Topic(id = entity.topic.id, name = entity.topic.name),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showInput) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 96.dp, vertical = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(28.dp),
            ) {
                Text(
                    text = "输入关键词",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocusRequester)
                        .tvTextEntry()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionPill(text = "搜索", onClick = { submit(query) })
                    TvActionPill(text = "取消", onClick = { showInput = false })
                }
            }
        }
    }
}

@Composable
private fun TvSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun TvSuggestionRow(
    text: String,
    onClick: () -> Unit,
) {
    TvFocusable(onClick = onClick, shape = rowShape, modifier = Modifier.fillMaxWidth()) { scope ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .tvFocusSurface(
                    scope,
                    if (scope.focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                ).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TvSearchResultRow(
    entity: SearchEntity,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val title = when (entity) {
        is SearchEntity.Content -> entity.item.title
        is SearchEntity.Person -> entity.person.name
        is SearchEntity.Topic -> entity.topic.name
    }
    val subtitle = when (entity) {
        is SearchEntity.Content -> listOfNotNull(entity.item.authorName, entity.item.details).joinToString(" · ")
        is SearchEntity.Person -> entity.person.headline
        is SearchEntity.Topic -> entity.excerpt
    }
    TvFocusable(onClick = onClick, focusRequester = focusRequester, shape = rowShape, modifier = Modifier.fillMaxWidth()) { scope ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .tvFocusSurface(
                    scope,
                    if (scope.focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                ).padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = title.ifBlank { "（无标题）" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
