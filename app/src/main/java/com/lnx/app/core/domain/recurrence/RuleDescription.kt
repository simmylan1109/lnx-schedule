package com.lnx.app.core.domain.recurrence

import com.lnx.app.core.common.LnxLocale
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.util.Locale

/**
 * 重复规则的文字描述(spec §3.6 详情卡与编辑器折叠行共用同一份)。
 *
 * 纯函数、可 JVM 单测、**不依赖 Android 的本地化框架**:`locale` 由调用方传进来,
 * 界面层用 `LocalLnxLocale.current` 取。这样 domain 层不会被 `Context` 污染,
 * 中英文两套描述也能直接用单测钉住(见 `RuleDescriptionTest`)。
 */
object RuleDescription {

    fun of(rule: EventRule, locale: Locale = Locale.SIMPLIFIED_CHINESE): String {
        val zh = LnxLocale.isChinese(locale)
        val base = when (rule.type) {
            RuleType.NONE -> return if (zh) "不重复" else "Does not repeat"
            RuleType.DAILY -> when {
                rule.interval <= 1 -> if (zh) "每天重复" else "Every day"
                zh -> "每 ${rule.interval} 天重复"
                else -> "Every ${rule.interval} days"
            }

            RuleType.WEEKLY -> {
                val days = rule.weekdays.sortedBy { it.value }
                    .joinToString(if (zh) "、" else ", ") { LnxLocale.weekday(it.value, locale) }
                when {
                    days.isEmpty() -> if (zh) "每周重复" else "Every week"
                    // 中文"每"+"周三"="每周三";不要写成"每周"+"周三"="每周周三"。
                    // 英文是 "Every Tuesday" / "Every 2 weeks on Tuesday, Thursday"
                    rule.interval <= 1 && zh -> "每${days}重复"
                    rule.interval <= 1 -> "Every $days"
                    zh -> "每 ${rule.interval} 周的${days}重复"
                    else -> "Every ${rule.interval} weeks on $days"
                }
            }

            RuleType.MONTHLY -> {
                val nth = rule.monthlyNth ?: 1
                when (rule.monthlyMode) {
                    MonthlyMode.BY_NTH_WEEKDAY -> {
                        val dow = rule.monthlyWeekday?.let { LnxLocale.weekday(it.value, locale) } ?: "?"
                        if (zh) {
                            val every = if (rule.interval <= 1) "每月" else "每 ${rule.interval} 个月的"
                            "${every}第 $nth 个${dow}重复"
                        } else {
                            "Monthly on the $nth $dow"
                        }
                    }

                    else -> {
                        val day = rule.monthlyDay ?: 1
                        if (zh) {
                            val every = if (rule.interval <= 1) "每月" else "每 ${rule.interval} 个月的"
                            "$every $day 日重复"
                        } else if (rule.interval <= 1) {
                            "Monthly on day $day"
                        } else {
                            "Every ${rule.interval} months on day $day"
                        }
                    }
                }
            }

            // 每年规则的月/日来自系列起点(规则里不存),这里只说频率
            RuleType.YEARLY -> if (zh) "每年重复" else "Every year"
        }
        return if (zh) "$base,${endText(rule.end, locale)}" else "$base, ${endText(rule.end, locale)}"
    }

    private fun endText(end: RuleEnd, locale: Locale): String {
        val zh = LnxLocale.isChinese(locale)
        return when (end) {
            is RuleEnd.Never -> if (zh) "永不结束" else "never ends"
            is RuleEnd.Until -> if (zh) {
                "到 ${end.date.year} 年 ${end.date.monthValue} 月 ${end.date.dayOfMonth} 日结束"
            } else {
                "ends on ${LnxLocale.monthDay(end.date, locale)}"
            }

            is RuleEnd.Count -> if (zh) {
                "重复 ${end.times} 次后结束"
            } else {
                "ends after ${end.times} times"
            }
        }
    }
}
