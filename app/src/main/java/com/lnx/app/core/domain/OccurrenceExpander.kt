package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.recurrence.RecurrenceEngine
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 把母事件与例外展开成可见区间内的"发生"(spec §4.4)。
 * 半开区间语义:occurrence.start < rangeEnd && occurrence.end > rangeStart。
 */
interface OccurrenceExpander {
    fun expand(
        events: List<Event>,
        exceptions: List<EventException>,
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime,
    ): List<Occurrence>
}

/**
 * M4 完整版:重复母事件按 RecurrenceEngine 现场展开(不落库),例外按"原本发生日"匹配
 * (取消的隐藏,改过的按 override 显示);改期搬进窗口的例外也补回来。
 * M2 的"只返回首例"中间态已被本实现取代(测试同步重写,见 OccurrenceExpanderTest)。
 */
class DefaultOccurrenceExpander @Inject constructor() : OccurrenceExpander {

    override fun expand(
        events: List<Event>,
        exceptions: List<EventException>,
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime,
    ): List<Occurrence> {
        val byMaster = exceptions.groupBy { it.masterId }
        val out = mutableListOf<Occurrence>()

        events.forEach { event ->
            val masterExceptions = byMaster[event.id].orEmpty()
            if (event.rule.type == RuleType.NONE) {
                // 单次事件:区间重叠即一条(例外理论上不存在,防御性忽略)
                if (event.start < rangeEnd && event.end > rangeStart) {
                    out += Occurrence(event, event.start, event.end)
                }
                return@forEach
            }

            val duration = Duration.between(event.start, event.end)
            val handledDates = mutableSetOf<LocalDate>()
            for (slot in RecurrenceEngine.expand(event.rule, event.start, duration, rangeStart, rangeEnd)) {
                val originalDate = slot.start.toLocalDate()
                handledDates += originalDate
                val exception = masterExceptions.firstOrNull { it.originalDate == originalDate }
                if (exception?.cancelled == true) continue
                if (exception == null) {
                    out += Occurrence(event.copy(start = slot.start, end = slot.end), slot.start, slot.end, originalDate)
                } else {
                    val override = exception.override
                    val start = override?.start ?: slot.start
                    val end = override?.end ?: slot.end
                    if (start < rangeEnd && end > rangeStart) {
                        out += Occurrence(merged(event, override, start, end), start, end, originalDate)
                    }
                }
            }
            // 改期搬进窗口的例外:原本发生日在窗口外(引擎没生成那次),但 override 落在窗口里
            for (exception in masterExceptions) {
                if (exception.cancelled || exception.originalDate in handledDates) continue
                val override = exception.override ?: continue
                if (override.start < rangeEnd && override.end > rangeStart) {
                    out += Occurrence(
                        merged(event, override, override.start, override.end),
                        override.start,
                        override.end,
                        exception.originalDate,
                    )
                }
            }
        }
        return out.sortedWith(compareBy({ it.start }, { it.end }, { it.event.id }))
    }

    /** 例外只换字段组,规则一律沿用母事件(单次例外不可改规则,v0.1 裁定) */
    private fun merged(master: Event, override: Event?, start: LocalDateTime, end: LocalDateTime): Event =
        if (override == null) master.copy(start = start, end = end) else override.copy(rule = master.rule)
}
