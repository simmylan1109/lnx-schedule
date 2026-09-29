package com.lnx.app.core.domain.model

import java.time.LocalDate

/**
 * 重复事件的单次例外(spec §4.3)。
 * - cancelled = true:那次被取消,展开时不出现;
 * - override 非 null:那次以 override 的**完整字段**显示(时间/标题等;spec §4.3"override 字段组")。
 *   单次例外不改重复规则本身(v0.1 裁定:改规则请用"本次及以后"或"全部"),展开时规则一律沿用母事件。
 */
data class EventException(
    val masterId: String,
    val originalDate: LocalDate,
    val cancelled: Boolean = false,
    val override: Event? = null,
)
