package com.lnx.app.core.common

import java.time.DayOfWeek
import java.time.LocalDate
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

/** spec §3.2:周一起始(ISO) */
fun weekStartOf(date: LocalDate): LocalDate =
    date.with(DayOfWeek.MONDAY)

private val PAGE_EPOCH: LocalDate = LocalDate.of(1970, 1, 5) // 周一

fun dateToPage(date: LocalDate): Int =
    ChronoUnit.WEEKS.between(PAGE_EPOCH, weekStartOf(date)).toInt()

fun pageToDate(page: Int): LocalDate = PAGE_EPOCH.plusWeeks(page.toLong())
