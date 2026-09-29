package com.lnx.app.core.domain.recurrence

import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType

/**
 * 重复规则的中文描述(spec §3.6 详情卡"重复规则描述"、编辑器折叠行共用同一份文案)。
 * 纯函数,可单测;不依赖本地化框架(v0.1 界面中文固定,spec §10)。
 */
object RuleDescription {
    private val WEEKDAY_CN = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    private fun weekdayCn(value: Int): String = WEEKDAY_CN.getOrElse(value - 1) { "?" }

    fun of(rule: EventRule): String {
        val base = when (rule.type) {
            RuleType.NONE -> return "不重复"
            RuleType.DAILY -> if (rule.interval <= 1) "每天重复" else "每 ${rule.interval} 天重复"
            RuleType.WEEKLY -> {
                val days = rule.weekdays.sortedBy { it.value }.joinToString("、") { weekdayCn(it.value) }
                when {
                    days.isEmpty() -> "每周重复"
                    // "每" + "周三" = "每周三";不要写成"每周"+"周三"= "每周周三"
                    rule.interval <= 1 -> "每${days}重复"
                    else -> "每 ${rule.interval} 周的${days}重复"
                }
            }

            RuleType.MONTHLY -> {
                val every = if (rule.interval <= 1) "每月" else "每 ${rule.interval} 个月的"
                when (rule.monthlyMode) {
                    MonthlyMode.BY_NTH_WEEKDAY -> {
                        val nth = rule.monthlyNth ?: 1
                        val dow = rule.monthlyWeekday?.let { weekdayCn(it.value) } ?: "?"
                        "${every}第 $nth 个${dow}重复"
                    }

                    else -> "$every ${rule.monthlyDay ?: 1} 日重复"
                }
            }

            // 每年规则的月/日来自系列起点(规则里不存),这里只说频率
            RuleType.YEARLY -> "每年重复"
        }
        return "$base,${endText(rule.end)}"
    }

    private fun endText(end: RuleEnd): String = when (end) {
        is RuleEnd.Never -> "永不结束"
        is RuleEnd.Until -> "到 ${end.date.year} 年 ${end.date.monthValue} 月 ${end.date.dayOfMonth} 日结束"
        is RuleEnd.Count -> "重复 ${end.times} 次后结束"
    }
}
