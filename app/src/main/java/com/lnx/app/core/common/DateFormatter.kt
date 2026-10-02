package com.lnx.app.core.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 日期文案。中文/英文走 [LnxLocale] 的两套格式,`locale` 由界面层传下来
 * (Compose 里用 `LocalLnxLocale.current`)。
 *
 * 保留 [dayOfWeekCn] 是因为中文界面里到处直接用它;正式路径请用
 * `LnxLocale.weekday(date, locale)` —— 英文界面下它会给出 `Tue`。
 */
fun dayOfWeekCn(date: LocalDate): String = LnxLocale.weekday(date, Locale.SIMPLIFIED_CHINESE)

fun formatTitle(date: LocalDate, locale: Locale): String = LnxLocale.title(date, locale)

/**
 * 备份时刻的文案(v0.2 补欠账 ②):「2026/10/2 15:30」。
 * 用系统自带的本地化日期+时间格式,不自己拼 —— 自己拼出来的英文版是用户挑不出错也看不惯的那种。
 */
fun formatBackupTime(millis: Long, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * 该日期所在周的**周起始日**(spec §3.11 ①:周一(默认)或周日,可在设置里改)。
 * 默认周一,老调用方不用改。
 *
 * 周日起始**必须用减法**,不能写 `date.with(DayOfWeek.MONDAY)` 的对称形式
 * `date.with(DayOfWeek.SUNDAY)`:后者的语义是"同一个 ISO 周(周一起算)里的那个周日",
 * 对周三会返回**之后**的周日(10-04),而不是本周的周首(09-27)。这个 bug 是被
 * `WeekLogicTest` 抓出来的 —— 当时它让"周日起始"整整错位一周。
 */
fun weekStartOf(date: LocalDate, mondayFirst: Boolean = true): LocalDate =
    if (mondayFirst) {
        date.with(DayOfWeek.MONDAY)
    } else {
        // ISO: 周一=1 … 周日=7;周日起始时,周日自己偏移 0,周一偏移 1,……
        date.minusDays((date.dayOfWeek.value % 7).toLong())
    }

/** 两种周起始各自的页锚点(都是各自的"周起始日") */
private val PAGE_EPOCH_MONDAY: LocalDate = LocalDate.of(1970, 1, 5) // 周一
private val PAGE_EPOCH_SUNDAY: LocalDate = LocalDate.of(1970, 1, 4) // 周日

fun dateToPage(date: LocalDate, mondayFirst: Boolean = true): Int =
    ChronoUnit.WEEKS.between(
        if (mondayFirst) PAGE_EPOCH_MONDAY else PAGE_EPOCH_SUNDAY,
        weekStartOf(date, mondayFirst),
    ).toInt()

fun pageToDate(page: Int, mondayFirst: Boolean = true): LocalDate =
    (if (mondayFirst) PAGE_EPOCH_MONDAY else PAGE_EPOCH_SUNDAY).plusWeeks(page.toLong())
