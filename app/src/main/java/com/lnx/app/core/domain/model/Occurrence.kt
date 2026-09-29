package com.lnx.app.core.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 一次"发生":非重复事件下与 Event 同起同止;
 * 重复事件由引擎按规则展开生成,同一 Event 可产生多个 Occurrence。
 *
 * [originalDate] = 该次"原本发生日"(未改期前的日期),例外表按它匹配(spec §4.3/§4.4);
 * 非重复事件为 null。改过期的发生仍携带原发生日,再编辑"仅本次"时才能写回同一条例外。
 */
data class Occurrence(
    val event: Event,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val originalDate: LocalDate? = null,
)
