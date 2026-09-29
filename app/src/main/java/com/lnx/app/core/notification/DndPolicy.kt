package com.lnx.app.core.notification

import java.time.LocalDateTime

/**
 * 免打扰静默策略(spec §3.8):时段内提醒**照发**,只是不响铃不震动。
 * 响铃/震动本身交给系统通知设置,App 只决定这条通知要不要静音。
 *
 * 窗口用"当天第几分钟"表示,[startMinute, endMinute);跨午夜(如 22:00–08:00)
 * 时 start > end,凌晨的分钟数小于 start 即算在窗口内。
 * 起止相同视为空窗口(防呆:否则用户把起止设成同一分钟会变成"全天静默")。
 */
object DndPolicy {

    fun isSilent(
        at: LocalDateTime,
        enabled: Boolean,
        startMinute: Int,
        endMinute: Int,
    ): Boolean {
        if (!enabled) return false
        if (startMinute == endMinute) return false
        val minute = at.hour * 60 + at.minute
        return if (startMinute < endMinute) {
            minute in startMinute until endMinute
        } else {
            // 跨午夜:夜里到零点后属于同一窗口
            minute >= startMinute || minute < endMinute
        }
    }
}
