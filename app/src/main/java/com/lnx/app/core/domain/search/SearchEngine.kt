package com.lnx.app.core.domain.search

import com.lnx.app.core.domain.OccurrenceExpander
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.RuleType
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把命中的原始事件变成可展示的结果列表(spec §3.9)。纯逻辑,可在 JVM 单测里跑。
 *
 * 三件事:
 * 1. **下次发生**:重复事件不能拿系列的起点当日期(那可能是三年前),要现算从 [now]
 *    起的第一次发生;例外表照常生效(被取消的那次跳过,改期的那次按 override 算)。
 * 2. **排序**:spec 只说"覆盖所有日期(含过去)",没说顺序。这里定成
 *    **没结束的排前面(近的先)、已结束的排后面(近的先)** —— 直接全局升序的话,
 *    搜"周会"会被五年前的同名日程顶满整屏,等于搜不到。
 * 3. **限量**:超量截断,避免几万个结果把列表和内存一起拖垮。
 */
@Singleton
class SearchEngine @Inject constructor(
    private val expander: OccurrenceExpander,
) {

    fun search(
        events: List<Event>,
        exceptions: List<EventException>,
        now: LocalDateTime,
        limit: Int = DEFAULT_LIMIT,
    ): List<SearchResult> {
        if (events.isEmpty()) return emptyList()
        val byMaster = exceptions.groupBy { it.masterId }
        val results = events.map { event -> toResult(event, byMaster[event.id].orEmpty(), now) }
        val upcoming = results.filter { it.start >= now }.sortedBy { it.start }
        val past = results.filter { it.start < now }.sortedByDescending { it.start }
        return (upcoming + past).take(limit)
    }

    private fun toResult(event: Event, exceptions: List<EventException>, now: LocalDateTime): SearchResult {
        if (event.rule.type == RuleType.NONE) {
            return SearchResult(event, event.start, event.end, recurring = false, hasUpcoming = false)
        }
        val next = nextOccurrence(event, exceptions, now)
        return if (next != null) {
            SearchResult(event, next.start, next.end, recurring = true, hasUpcoming = true)
        } else {
            // 已经结束的系列:没有"下次",退回系列起点,界面不写「下次发生」
            SearchResult(event, event.start, event.end, recurring = true, hasUpcoming = false)
        }
    }

    /**
     * 从 [from] 起的第一次发生。分段向前找而不是一上来就展开几年:
     * 每日事件的第一次尝试就命中(只算一个月),只有"每月/每年"这种稀疏规则才会往后扩,
     * 免得每条结果都白算几千个槽位。
     */
    private fun nextOccurrence(
        event: Event,
        exceptions: List<EventException>,
        from: LocalDateTime,
    ): Occurrence? {
        var windowEnd = from
        for (spanDays in LOOKAHEAD_DAYS) {
            windowEnd = windowEnd.plusDays(spanDays)
            val hit = expander.expand(listOf(event), exceptions, from, windowEnd).firstOrNull()
            if (hit != null) return hit
        }
        return null
    }

    companion object {
        const val DEFAULT_LIMIT = 100

        /** 逐段向后找"下次发生"的跨度(天),累计约 5 年 */
        private val LOOKAHEAD_DAYS = longArrayOf(32, 400, 1_429)

        /**
         * 把用户输入转成 SQL `LIKE` 的模式串(`%词%`),并转义 LIKE 的通配符。
         * 不转义的话搜 `100%` 会变成"以 100 开头",搜 `a_b` 会把任意字符算进去。
         */
        fun likePattern(query: String): String {
            val escaped = query.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
