package com.lnx.app.core.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语言与日期文案(spec §10)。
 *
 * 这些是"程序生成的文字"的中英两套形状,最容易在改版时悄悄退回单语言 ——
 * 界面文案有 `values-en` 兜底,这里的分支没有,只能靠测试钉住。
 */
class LnxLocaleTest {

    private val zh = Locale.SIMPLIFIED_CHINESE
    private val en = Locale.ENGLISH
    private val date = LocalDate.of(2026, 9, 29) // 周二
    private val time = LocalDateTime.of(2026, 9, 29, 14, 30)

    @Test
    fun `中文取中文语言包其余按英文`() {
        assertEquals(zh, LnxLocale.resolve("zh"))
        assertEquals(en, LnxLocale.resolve("en"))
        // 跟随系统时,系统是中文就中文;不是中文就落到英文(只有中英两套资源)
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            assertEquals(zh, LnxLocale.resolve("system"))
            Locale.setDefault(Locale.FRANCE)
            assertEquals(en, LnxLocale.resolve("system"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `星期中英各自成形`() {
        assertEquals("周二", LnxLocale.weekday(date, zh))
        assertEquals("Tue", LnxLocale.weekday(date, en))
        // ISO 序号:1 = 周一
        assertEquals("周一", LnxLocale.weekday(1, zh))
        assertEquals("Mon", LnxLocale.weekday(1, en))
    }

    @Test
    fun `标题中英词序不同`() {
        assertEquals("9月 · 29日 周二", LnxLocale.title(date, zh))
        assertEquals("Sep 29, Tue", LnxLocale.title(date, en))
    }

    @Test
    fun `日期与时间中英各一套格式`() {
        assertEquals("9月29日 周二", LnxLocale.dateWithWeekday(date, zh))
        assertEquals("Sep 29, Tue", LnxLocale.dateWithWeekday(date, en))

        assertEquals("9月29日", LnxLocale.monthDay(date, zh))
        assertEquals("Sep 29", LnxLocale.monthDay(date, en))

        assertEquals("9月29日 周二 14:30", LnxLocale.dateTime(time, zh))
        assertTrue(LnxLocale.dateTime(time, en).endsWith("2:30 PM"))

        // 英文习惯 12 小时制,中文 24 小时制
        assertEquals("14:30", LnxLocale.time(LocalTime.of(14, 30), zh))
        assertEquals("2:30 PM", LnxLocale.time(LocalTime.of(14, 30), en))
    }
}
