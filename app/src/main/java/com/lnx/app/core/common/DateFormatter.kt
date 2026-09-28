package com.lnx.app.core.common

import java.time.DayOfWeek
import java.time.LocalDate

private val DOW_CN = mapOf(
    DayOfWeek.MONDAY to "周一",
    DayOfWeek.TUESDAY to "周二",
    DayOfWeek.WEDNESDAY to "周三",
    DayOfWeek.THURSDAY to "周四",
    DayOfWeek.FRIDAY to "周五",
    DayOfWeek.SATURDAY to "周六",
    DayOfWeek.SUNDAY to "周日",
)

fun dayOfWeekCn(date: LocalDate): String = DOW_CN.getValue(date.dayOfWeek)

fun formatTitle(date: LocalDate): String =
    "${date.monthValue}月 · ${date.dayOfMonth}日 ${dayOfWeekCn(date)}"
