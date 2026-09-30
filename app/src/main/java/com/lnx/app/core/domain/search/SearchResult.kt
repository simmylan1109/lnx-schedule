package com.lnx.app.core.domain.search

import com.lnx.app.core.domain.model.Event
import java.time.LocalDateTime

/**
 * 一条搜索结果(spec §3.9)。
 *
 * [start]/[end] 是**展示用**的起止:重复事件给"下次发生"那一次(见 [SearchEngine]),
 * 单次事件给事件本身的起止(含过去 —— spec §3.9 明确要求覆盖所有日期)。
 * 界面文案用 [recurring]+[hasUpcoming] 决定要不要写「下次发生:」这一行:
 * 重复事件但已经结束(UNTIL/COUNT 用尽)时 [hasUpcoming] = false,退回显示系列起点。
 */
data class SearchResult(
    val event: Event,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val recurring: Boolean,
    val hasUpcoming: Boolean,
)

/**
 * 搜索跳转后的高亮目标(spec §3.9「定位 / 高亮该事件」)。
 * 带 [start] 是因为同一天里同一个重复事件可能出现两次(改期过),只按 id 会同时点亮两块。
 */
data class HighlightTarget(
    val eventId: String,
    val start: LocalDateTime,
)
