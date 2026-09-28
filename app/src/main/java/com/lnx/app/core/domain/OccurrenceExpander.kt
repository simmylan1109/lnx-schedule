package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.Occurrence
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * 把事件展开成可见区间内的"发生"。
 * 半开区间语义:occurrence.start < rangeEnd && occurrence.end > rangeStart。
 */
interface OccurrenceExpander {
    fun expand(
        events: List<Event>,
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime,
    ): List<Occurrence>
}

/**
 * M2 版本:只处理单次发生。
 * 重复事件也只返回首例(即事件自身那次),M4 会在不改变本接口的前提下换成完整引擎;
 * `重复事件本里程碑只返回首例` 的测试就是钉住这个中间态的。
 */
class BasicOccurrenceExpander @Inject constructor() : OccurrenceExpander {
    override fun expand(
        events: List<Event>,
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime,
    ): List<Occurrence> = events
        .mapNotNull { event ->
            if (event.start < rangeEnd && event.end > rangeStart) {
                Occurrence(event = event, start = event.start, end = event.end)
            } else {
                null
            }
        }
        .sortedWith(compareBy({ it.start }, { it.end }, { it.event.id }))
}
