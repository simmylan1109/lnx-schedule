package com.lnx.app.core.domain.recurrence

import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 规则中文描述(spec §3.6 详情卡 + 编辑器折叠行),文案逐句钉住 */
class RuleDescriptionTest {
    @Test
    fun `不重复`() {
        assertEquals("不重复", RuleDescription.of(EventRule()))
    }

    @Test
    fun `每天`() {
        assertEquals("每天重复,永不结束", RuleDescription.of(EventRule(RuleType.DAILY)))
        assertEquals("每 3 天重复,永不结束", RuleDescription.of(EventRule(RuleType.DAILY, interval = 3)))
    }

    @Test
    fun `每周单日与双日`() {
        assertEquals(
            "每周三重复,永不结束",
            RuleDescription.of(EventRule(RuleType.WEEKLY, weekdays = setOf(DayOfWeek.WEDNESDAY))),
        )
        assertEquals(
            "每周一、周五重复,永不结束",
            RuleDescription.of(EventRule(RuleType.WEEKLY, weekdays = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))),
        )    }

    @Test
    fun `每N周的星期几`() {
        assertEquals(
            "每 2 周的周一、周五重复,永不结束",
            RuleDescription.of(
                EventRule(RuleType.WEEKLY, interval = 2, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            ),
        )
    }

    @Test
    fun `每月按日期与按第N个星期X`() {
        assertEquals(
            "每月 15 日重复,永不结束",
            RuleDescription.of(
                EventRule(RuleType.MONTHLY, monthlyMode = MonthlyMode.BY_MONTHDAY, monthlyDay = 15),
            ),
        )
        assertEquals(
            "每月第 3 个周五重复,永不结束",
            RuleDescription.of(
                EventRule(
                    RuleType.MONTHLY,
                    monthlyMode = MonthlyMode.BY_NTH_WEEKDAY,
                    monthlyNth = 3,
                    monthlyWeekday = DayOfWeek.FRIDAY,
                ),
            ),
        )
    }

    @Test
    fun `每年`() {
        assertEquals("每年重复,永不结束", RuleDescription.of(EventRule(RuleType.YEARLY)))
    }

    @Test
    fun `三种结束条件`() {
        val daily = EventRule(RuleType.DAILY)
        assertEquals(
            "每天重复,到 2027 年 1 月 1 日结束",
            RuleDescription.of(daily.copy(end = RuleEnd.Until(LocalDate.parse("2027-01-01")))),
        )
        assertEquals(
            "每天重复,重复 5 次后结束",
            RuleDescription.of(daily.copy(end = RuleEnd.Count(5))),
        )
    }

    @Test
    fun `每周未选星期几时不编造具体日子`() {
        // 编辑中间态:引擎会回退到系列起点的星期,描述不许凭空造一天出来
        assertEquals("每周重复,永不结束", RuleDescription.of(EventRule(RuleType.WEEKLY)))
    }
}
