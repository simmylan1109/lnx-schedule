package com.lnx.app.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lnx.app.R
import com.lnx.app.core.common.LocalLnxLocale
import com.lnx.app.core.common.LnxLocale
import com.lnx.app.core.designsystem.EventColors
import com.lnx.app.core.domain.search.SearchResult

/**
 * 搜索页(spec §3.9):顶栏搜索图标进入,整页覆盖在日历之上。
 * 匹配范围与跳转由 ViewModel 负责,这里只画界面。
 */
@Composable
fun SearchScreen(
    query: String,
    results: List<SearchResult>,
    tagColors: Map<String, List<Int>>,
    onQueryChange: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 打开即聚焦:搜索页的价值全在"马上能打字",等用户再点一下输入框是白等
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("search_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("search_back")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.search_back),
                    )
                }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("search_field"),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.testTag("search_clear"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.search_clear),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            when {
                // 没输入时不显示空态文案:一进来就写「未找到相关日程」像坏了
                query.isBlank() -> Unit
                results.isEmpty() -> SearchEmpty()
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { "${it.event.id}@${it.start}" }) { result ->
                        SearchResultRow(
                            result = result,
                            tagColors = tagColors[result.event.id].orEmpty(),
                            onClick = { onResultClick(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmpty() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("search_empty"),
        )
    }
}

/**
 * 一条结果(spec §3.9):标题 / 日期时间 / 标签色点。
 * 重复事件那行写「下次发生:」,避免用户点进去才发现是三年前那一场。
 */
@Composable
private fun SearchResultRow(
    result: SearchResult,
    tagColors: List<Int>,
    onClick: () -> Unit,
) {
    val locale = LocalLnxLocale.current
    val event = result.event
    // 全天事件也要带日期(spec §3.9 结果列表要显示"日期时间"):
    // 只写"全天"的话,两条不同天的全天事件在结果里一模一样,
    // 重复 + 全天的组合还会渲染成「下次发生:全天」这种没有日期的话。
    val whenText = if (event.allDay) {
        stringResource(R.string.detail_all_day_single, LnxLocale.dateWithWeekday(result.start.toLocalDate(), locale))
    } else {
        "${LnxLocale.dateWithWeekday(result.start.toLocalDate(), locale)} " +
            LnxLocale.time(result.start.toLocalTime(), locale)
    }
    val subtitle = if (result.recurring && result.hasUpcoming) {
        stringResource(R.string.search_next_occurrence, whenText)
    } else {
        whenText
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("search_result_${event.id}"),
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tagColors.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    tagColors.forEach { slot ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EventColors.of(slot)),
                        )
                    }
                }
            }
        }
    }
}
