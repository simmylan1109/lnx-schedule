package com.lnx.app.core.domain.model

import java.time.LocalDateTime

/**
 * 一次"发生":非重复事件下与 Event 同起同止;
 * 重复事件由 M4 的引擎按规则展开生成,同一 Event 可产生多个 Occurrence。
 */
data class Occurrence(
    val event: Event,
    val start: LocalDateTime,
    val end: LocalDateTime,
)
