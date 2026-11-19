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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.HistoryStorage
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.filter.ContentOpenTracker
import com.github.zly2006.zhihu.icons.AppIcon
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.markdown.RenderMarkdown
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY
import com.github.zly2006.zhihu.tv.components.TvHint
import com.github.zly2006.zhihu.tv.components.TvHints
import com.github.zly2006.zhihu.tv.focus.LocalTvFocusedActionHost
import com.github.zly2006.zhihu.tv.focus.TvFocusable
import com.github.zly2006.zhihu.tv.focus.tvFocusSurface
import com.github.zly2006.zhihu.tv.input.TvAction
import com.github.zly2006.zhihu.tv.input.toTvActionOrNull
import com.github.zly2006.zhihu.ui.PinHtmlContent
import com.github.zly2006.zhihu.ui.article.rememberArticleAnswerNavigationState
import com.github.zly2006.zhihu.ui.loadPinDetail
import com.github.zly2006.zhihu.ui.loadQuestion
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerSwitchState
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.feed.QuestionFeedViewModel
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** 阅读页右侧操作列的一项。 */
@Immutable
private data class TvReaderAction(
    val icon: AppIcon,
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 手柄 / TV 模式的阅读页：文章、回答、问题与想法共用同一套正文框架。
 *
 * 正文只走 Compose Markdown（WebView 是废弃路径）；评论、点赞、收藏和回答切换都复用手机端同一批
 * `ArticleViewModel` / `QuestionFeedViewModel` 调用，不复制请求逻辑。
 */
@Composable
fun TvReaderScreen(
    destination: NavDestination,
    onOpenComments: (NavDestination) -> Unit,
) {
    when (destination) {
        is Article -> TvArticleReader(destination, onOpenComments)
        is Question -> TvQuestionReader(destination, onOpenComments)
        is Pin -> TvPinReader(destination, onOpenComments)
        else -> TvReaderUnsupported()
    }
}

@Composable
private fun TvReaderUnsupported() {
    TvHints(TvHint(TvAction.Back, "返回"))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "手柄模式暂不支持打开该内容",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TvArticleReader(
    article: Article,
    onOpenComments: (NavDestination) -> Unit,
) {
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val history = koinInject<HistoryStorage>()
    val contentOpens = koinInject<ContentOpenTracker>()
    val switchState = koinInject<ArticleAnswerSwitchState>()
    val settings = koinInject<SettingsStore>()
    val userMessages = rememberUserMessageSink()
    val navigator = LocalNavigator.current
    val viewModel: ArticleViewModel = viewModel {
        ArticleViewModel(article, environment.httpClient(), userMessages)
    }
    val answerNavigation = rememberArticleAnswerNavigationState(
        switchState = switchState,
        viewModel = viewModel,
        navigator = navigator,
        navController = null,
        answerSwitchMode = "vertical",
        readingQueueSourceId = null,
    )
    LaunchedEffect(article.type, article.id) {
        answerNavigation.prepareArticle()
        viewModel.loadArticle(environment, history, contentOpens, switchState)
        viewModel.loadCollections(environment)
    }

    val useTiqian = settings.getBoolean(DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
    val actions = buildList {
        add(
            TvReaderAction(
                icon = if (viewModel.voteUpState == VoteUpState.Up) AppIcons.ThumbUpFilled else AppIcons.ThumbUp,
                label = "赞同 ${viewModel.voteUpCount}",
                selected = viewModel.voteUpState == VoteUpState.Up,
                onClick = {
                    val next = if (viewModel.voteUpState == VoteUpState.Up) VoteUpState.Neutral else VoteUpState.Up
                    viewModel.toggleVoteUp(environment, next)
                },
            ),
        )
        add(
            TvReaderAction(
                icon = AppIcons.Comment,
                label = "评论 ${viewModel.commentCount}",
                onClick = { onOpenComments(article) },
            ),
        )
        add(
            TvReaderAction(
                icon = if (viewModel.isFavorited) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                label = "收藏",
                selected = viewModel.isFavorited,
                onClick = {
                    val collection = viewModel.collections.firstOrNull { it.isDefault } ?: viewModel.collections.firstOrNull()
                    if (collection == null) {
                        userMessages.showShortMessage("收藏夹还没有加载完成")
                    } else {
                        viewModel.toggleFavorite(collection.id, collection.isFavorited, environment)
                    }
                },
            ),
        )
        if (article.type == ArticleType.Answer) {
            add(
                TvReaderAction(
                    icon = AppIcons.ArrowUpward,
                    label = "上一个",
                    onClick = answerNavigation::navigateToPrevious,
                ),
            )
            add(
                TvReaderAction(
                    icon = AppIcons.ArrowDownward,
                    label = "下一个",
                    onClick = answerNavigation::navigateToNext,
                ),
            )
        }
    }

    TvReaderFrame(
        hints = buildList {
            add(TvHint(TvAction.Up, "滚动", key = "▲▼"))
            add(TvHint(TvAction.Right, "操作", key = "▶"))
            if (article.type == ArticleType.Answer) {
                add(TvHint(TvAction.PreviousItem, "上一篇", key = "L2"))
                add(TvHint(TvAction.NextItem, "下一篇", key = "R2"))
            }
            add(TvHint(TvAction.Confirm, "确定"))
            add(TvHint(TvAction.Back, "返回"))
        },
        actions = actions,
        onPreviousItem = if (article.type == ArticleType.Answer) answerNavigation::navigateToPrevious else null,
        onNextItem = if (article.type == ArticleType.Answer) answerNavigation::navigateToNext else null,
    ) { scrollState ->
        Text(
            text = viewModel.title.ifBlank { article.title },
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = viewModel.authorName.ifBlank { article.authorName },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (viewModel.authorBio.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = viewModel.authorBio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        if (viewModel.content.isBlank()) {
            Text(
                text = viewModel.loadFailure?.message ?: "正在加载…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RenderMarkdown(
                html = viewModel.content,
                scrollState = scrollState,
                selectable = false,
                enableScroll = false,
                useTiqianRenderer = useTiqian,
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun TvQuestionReader(
    question: Question,
    onOpenComments: (NavDestination) -> Unit,
) {
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val history = koinInject<HistoryStorage>()
    val contentOpens = koinInject<ContentOpenTracker>()
    val settings = koinInject<SettingsStore>()
    val blockedUsers = koinInject<ContentFilterDatabase>().blockedUserDao()
    val navigator = LocalNavigator.current
    val viewModel: QuestionFeedViewModel = viewModel(key = "tv_question_${question.questionId}") {
        QuestionFeedViewModel(question.questionId, settings, blockedUsers)
    }
    var detail by remember { mutableStateOf<com.github.zly2006.zhihu.data.DataHolder.Question?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(question.questionId) {
        if (viewModel.displayItems.isEmpty()) viewModel.refresh(environment)
        detail =
            try {
                loadQuestion(environment, history, contentOpens, question)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        loadFailed = detail == null
    }

    val listState = rememberLazyListState()
    val answerFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val fallbackActionRequester = remember { FocusRequester() }
    val bodyRequester = remember { FocusRequester() }
    val bodyInteraction = remember { MutableInteractionSource() }
    val bodyFocused by bodyInteraction.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionHost = LocalTvFocusedActionHost.current
    val scrollOwner = remember { Any() }
    DisposableEffect(bodyFocused, actionHost) {
        if (!bodyFocused) return@DisposableEffect onDispose {}
        val step = with(density) { 150.dp.toPx() }
        val dispose = actionHost.registerScroll(scrollOwner) { action ->
            when (action) {
                TvAction.Up -> {
                    if (!listState.canScrollBackward) return@registerScroll false
                    scope.launch { listState.animateScrollBy(-step) }
                    true
                }

                TvAction.Down -> {
                    if (listState.canScrollForward) {
                        scope.launch { listState.animateScrollBy(step) }
                        true
                    } else {
                        // 长图滚到底后，把焦点交给当前可见的第一条回答；不能再用可能已被回收的第一条 requester。
                        val key = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key is String && answerFocusRequesters.containsKey(it.key) }
                            ?.key as? String
                        val requester = key?.let(answerFocusRequesters::get) ?: return@registerScroll false
                        runCatching { requester.requestFocus() }.isSuccess
                    }
                }

                else -> {
                    false
                }
            }
        }
        onDispose(dispose)
    }
    // 详情加载失败或没有回答时，先聚焦问题正文；这样长图可以滚动，B 也有焦点目标。
    LaunchedEffect(Unit) {
        repeat(3) {
            withFrameNanos { }
            if (runCatching { bodyRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        runCatching { fallbackActionRequester.requestFocus() }
    }
    val useTiqian = settings.getBoolean(DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
    TvHints(
        TvHint(TvAction.Up, "移动"),
        TvHint(TvAction.Confirm, "打开回答"),
        TvHint(TvAction.Back, "返回"),
    )
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(bodyRequester)
                .focusProperties {
                    up = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = fallbackActionRequester
                }.focusable(interactionSource = bodyInteraction),
            contentPadding = androidx.compose.foundation.layout
                .PaddingValues(bottom = 32.dp),
        ) {
            item(key = "question_header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 24.dp)) {
                    Text(
                        text = detail?.title ?: question.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    val description = detail?.detail.orEmpty()
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        RenderMarkdown(
                            html = description,
                            selectable = false,
                            enableScroll = false,
                            useTiqianRenderer = useTiqian,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when {
                            loadFailed -> "问题详情加载失败"
                            detail == null -> "正在加载…"
                            else -> "${detail?.answerCount ?: 0} 个回答 · ${detail?.followerCount ?: 0} 人关注"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(viewModel.displayItems, key = { _, item -> item.stableKey }) { index, item ->
                TvAnswerRow(
                    index = index,
                    authorName = item.authorName.orEmpty(),
                    excerpt = item.summary.orEmpty(),
                    details = item.details,
                    focusRequester = answerFocusRequesters.getOrPut(item.stableKey) { FocusRequester() },
                    onClick = { item.navDestination?.let { navigator.onNavigate(it) } },
                )
            }
        }
        TvReaderActionColumn(
            actions = listOf(
                TvReaderAction(AppIcons.Comment, "评论") { onOpenComments(question) },
                TvReaderAction(AppIcons.Refresh, "刷新") { viewModel.refresh(environment) },
            ),
            firstFocusRequester = fallbackActionRequester,
            bodyFocusRequester = bodyRequester,
            modifier = Modifier.fillMaxHeight().width(96.dp),
        )
    }
}

@Composable
private fun TvAnswerRow(
    index: Int,
    authorName: String,
    excerpt: String,
    details: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    TvFocusable(
        onClick = onClick,
        focusRequester = focusRequester,
        shape = shape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 6.dp),
    ) { scope ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .tvFocusSurface(
                    scope,
                    if (scope.focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                ).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = authorName.ifBlank { "匿名用户" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (excerpt.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TvPinReader(
    pin: Pin,
    onOpenComments: (NavDestination) -> Unit,
) {
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val history = koinInject<HistoryStorage>()
    val contentOpens = koinInject<ContentOpenTracker>()
    var detail by remember { mutableStateOf<com.github.zly2006.zhihu.data.DataHolder.Pin?>(null) }
    LaunchedEffect(pin.id) {
        detail =
            try {
                loadPinDetail(environment, history, contentOpens, pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
    }

    TvReaderFrame(
        hints = listOf(
            TvHint(TvAction.Up, "滚动", key = "▲▼"),
            TvHint(TvAction.Right, "操作", key = "▶"),
            TvHint(TvAction.Confirm, "确定"),
            TvHint(TvAction.Back, "返回"),
        ),
        actions = listOf(TvReaderAction(AppIcons.Comment, "评论") { onOpenComments(pin) }),
    ) { _ ->
        val content = detail
        Text(
            text = content?.author?.name ?: pin.authorName.ifBlank { "想法" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(20.dp))
        if (content == null) {
            Text(
                text = "正在加载…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PinHtmlContent(content.contentHtml)
        }
        Spacer(Modifier.height(80.dp))
    }
}

/**
 * 阅读页框架：左侧可滚动正文，右侧操作列。
 *
 * 正文是单独焦点目标，并取消上下方向焦点移动，让方向键用于滚动；向右才能进入操作列。
 */
@Composable
private fun TvReaderFrame(
    hints: List<TvHint>,
    actions: List<TvReaderAction>,
    onPreviousItem: (() -> Unit)? = null,
    onNextItem: (() -> Unit)? = null,
    content: @Composable (androidx.compose.foundation.ScrollState) -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bodyFocusRequester = remember { FocusRequester() }
    val firstActionFocusRequester = remember { FocusRequester() }
    val bodyInteraction = remember { MutableInteractionSource() }
    val bodyFocused by bodyInteraction.collectIsFocusedAsState()
    val actionHost = LocalTvFocusedActionHost.current
    val scrollOwner = remember { Any() }
    val contentOwner = remember { Any() }
    val latestOnPreviousItem by rememberUpdatedState(onPreviousItem)
    val latestOnNextItem by rememberUpdatedState(onNextItem)
    DisposableEffect(actionHost, contentOwner) {
        val dispose = actionHost.registerContentAction(contentOwner) { action ->
            when (action) {
                TvAction.PreviousItem -> latestOnPreviousItem?.let {
                    it()
                    true
                } ?: false

                TvAction.NextItem -> latestOnNextItem?.let {
                    it()
                    true
                } ?: false

                else -> false
            }
        }
        onDispose(dispose)
    }
    // 方向键先交给正文滚动，再考虑焦点移动；否则根节点会先把 Up/Down 当成焦点搜索消耗掉。
    DisposableEffect(bodyFocused, actionHost, scrollOwner) {
        if (!bodyFocused) return@DisposableEffect onDispose {}
        val step = with(density) { 150.dp.toPx() }
        val dispose = actionHost.registerScroll(scrollOwner) { action ->
            when (action) {
                TvAction.Up -> {
                    scope.launch { scrollState.animateScrollBy(-step) }
                    true
                }

                TvAction.Down -> {
                    scope.launch { scrollState.animateScrollBy(step) }
                    true
                }

                else -> {
                    false
                }
            }
        }
        onDispose(dispose)
    }
    LaunchedEffect(Unit) {
        // 等首帧完成组合，焦点目标挂上 FocusRequester 后再请求，否则会静默失败落到操作列。
        withFrameNanos { }
        runCatching { bodyFocusRequester.requestFocus() }
    }
    TvHints(hints)

    Row(Modifier.fillMaxSize()) {
        // 正文本身是可聚焦的滚动区域；用左侧强调条提示「现在方向键在滚动正文」。
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (bodyFocused) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(bodyFocusRequester)
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    // 向右固定进入操作列第一项，避免几何搜索落到中间按钮。
                    if (actions.isNotEmpty()) right = firstActionFocusRequester
                }.onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = with(density) { 150.dp.toPx() }
                    when (event.toTvActionOrNull()) {
                        TvAction.Up -> {
                            scope.launch { scrollState.animateScrollBy(-step) }
                            true
                        }

                        TvAction.Down -> {
                            scope.launch { scrollState.animateScrollBy(step) }
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.focusable(interactionSource = bodyInteraction)
                .verticalScroll(scrollState)
                .padding(horizontal = 40.dp),
        ) {
            Column(
                Modifier
                    .widthIn(max = 980.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            ) {
                content(scrollState)
            }
        }
        TvReaderActionColumn(
            actions = actions,
            firstFocusRequester = firstActionFocusRequester,
            bodyFocusRequester = bodyFocusRequester,
            modifier = Modifier.fillMaxHeight().width(96.dp),
        )
    }
}

@Composable
private fun TvReaderActionColumn(
    actions: List<TvReaderAction>,
    firstFocusRequester: FocusRequester?,
    bodyFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.forEachIndexed { index, action ->
            TvFocusable(
                onClick = action.onClick,
                focusRequester = if (index == 0) firstFocusRequester else null,
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .then(
                        if (bodyFocusRequester != null) {
                            Modifier.focusProperties { left = bodyFocusRequester }
                        } else {
                            Modifier
                        },
                    ),
            ) { scope ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .tvFocusSurface(
                            scope,
                            if (scope.focused) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else if (action.selected) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        ).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        icon = action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (scope.focused) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else if (action.selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (scope.focused) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
