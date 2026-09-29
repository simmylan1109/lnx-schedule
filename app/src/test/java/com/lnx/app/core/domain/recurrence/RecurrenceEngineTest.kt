package com.lnx.app.core.domain.recurrence

import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重复展开引擎(spec §3.7/§4.4)。纯函数,全部用固定日期,不依赖系统时钟。
 * 语义钉子见 RecurrenceEngine 的文档注释。
 */
class RecurrenceEngineTest {
    // 基准:系列起点 2026-09-28(周一)09:00-10:00;可见区间 [10-01, 11-01)
    private val rangeS = LocalDateTime.parse("2026-10-01T00:00")
    private val rangeE = LocalDateTime.parse("2026-11-01T00:00")

    private fun rule(
        type: RuleType,
        interval: Int = 1,
        weekdays: Set<DayOfWeek> = emptySet(),
        monthlyMode: MonthlyMode? = null,
        monthlyDay: Int? = null,
        monthlyNth: Int? = null,
        monthlyWeekday: DayOfWeek? = null,
        end: RuleEnd = RuleEnd.Never,
    ) = EventRule(type, interval, weekdays, monthlyMode, monthlyDay, monthlyNth, monthlyWeekday, end)

    private fun series(
        start: String,
        rule: EventRule,
        duration: Duration = Duration.ofHours(1),
        rangeStart: LocalDateTime = rangeS,
        rangeEnd: LocalDateTime = rangeE,
    ) = RecurrenceEngine.expand(rule, LocalDateTime.parse(start), duration, rangeStart, rangeEnd)

    // —— 每天 ——

    @Test
    fun `每天_31天区间命中31次`() {
        assertEquals(31, series("2026-09-28T09:00", rule(RuleType.DAILY)).size)
    }

    @Test
    fun `每天_时刻与时长沿用系列起点`() {
        val slots = series("2026-09-28T09:00", rule(RuleType.DAILY))
        assertTrue(slots.all { it.start.toLocalTime().toString() == "09:00" })
        assertEquals(Duration.ofHours(1), Duration.between(slots[0].start, slots[0].end))
    }

    @Test
    fun `每3天_跳过间隔`() {
        // 9-28 起每 3 天:10-01、04、…、10-31,区间内 11 次
        assertEquals(11, series("2026-09-28T09:00", rule(RuleType.DAILY, 3)).size)
    }

    @Test
    fun `每天_起点远早于区间仍正确`() {
        // 1970 年起的每日事件:快进不能丢次数,也不能把窗口外的算进来
        val slots = series("1970-01-01T09:00", rule(RuleType.DAILY))
        assertEquals(31, slots.size)
        assertEquals(1, slots.first().start.dayOfMonth)
    }

    // —— 每周 ——

    @Test
    fun `每周三_区间4个周三`() {
        val slots = series("2026-09-28T09:00", rule(RuleType.WEEKLY, 1, setOf(DayOfWeek.WEDNESDAY)))
        assertEquals(listOf(7, 14, 21, 28), slots.map { it.start.dayOfMonth })
    }

    @Test
    fun `每周_周一和周五双日`() {
        // 起点 9-28 本身是周一:区间内 10-02、05、09、12、16、19、23、26、30
        assertEquals(9, series("2026-09-28T09:00", rule(RuleType.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))).size)
    }

    @Test
    fun `每周_起点周的星期几只要晚于起点就发生`() {
        // 系列起点周三 9-30,规则周一+周五:周一 9-28 早于起点不发生,首例是周五 10-02
        val slots = series("2026-09-30T09:00", rule(RuleType.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), rangeStart = LocalDateTime.parse("2026-09-28T00:00"))
        assertEquals(LocalDate.parse("2026-10-02"), slots.first().start.toLocalDate())
    }

    @Test
    fun `每2周_只有隔周发生`() {
        // 起点周(9-28)为第 0 周:10-14、10-28
        val slots = series("2026-09-28T09:00", rule(RuleType.WEEKLY, 2, setOf(DayOfWeek.WEDNESDAY)))
        assertEquals(listOf(14, 28), slots.map { it.start.dayOfMonth })
    }

    @Test
    fun `每2周_双日_区间5次`() {
        // 第 0 周 10-02(周五),第 2 周 10-12、10-16,第 4 周 10-26、10-30
        val slots = series("2026-09-28T09:00", rule(RuleType.WEEKLY, 2, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
        assertEquals(listOf(2, 12, 16, 26, 30), slots.map { it.start.dayOfMonth })
    }

    @Test
    fun `每周_未选星期几回退起点星期`() {
        val slots = series("2026-09-28T09:00", rule(RuleType.WEEKLY, 1, emptySet()))
        assertTrue(slots.all { it.start.dayOfWeek == DayOfWeek.MONDAY })
    }

    // —— 每月 ——

    @Test
    fun `每月第N天_短月跳过`() {
        // 每月 31 号:2 月等短月不发生,区间内只有 10-31
        val slots = series("2026-01-31T09:00", rule(RuleType.MONTHLY, 1, monthlyMode = MonthlyMode.BY_MONTHDAY, monthlyDay = 31))
        assertEquals(listOf(31), slots.map { it.start.dayOfMonth })
    }

    @Test
    fun `每月第N天_默认沿用起点日`() {
        val slots = series("2026-09-15T09:00", rule(RuleType.MONTHLY, 1, monthlyMode = MonthlyMode.BY_MONTHDAY, monthlyDay = null))
        assertEquals(listOf(15), slots.map { it.start.dayOfMonth })
    }

    @Test
    fun `每月第3个周五_区间内仅10月一次`() {
        // 账号裁定:计划示例期望 10-16 与 11-20,但 11-20 落在区间终点 11-01 之后,只应有 10-16
        val slots = series("2026-09-18T09:00", rule(RuleType.MONTHLY, 1, monthlyMode = MonthlyMode.BY_NTH_WEEKDAY, monthlyNth = 3, monthlyWeekday = DayOfWeek.FRIDAY))
        assertEquals(listOf(16), slots.map { it.start.dayOfMonth })
    }

    @Test
    fun `每月第5个星期X_不存在则该月跳过`() {
        // 2026 年 10 月只有 4 个周一(5、12、19、26):第 5 个周一不发生,11 月第 5 个周一(30)在区间外
        val slots = series("2026-09-07T09:00", rule(RuleType.MONTHLY, 1, monthlyMode = MonthlyMode.BY_NTH_WEEKDAY, monthlyNth = 5, monthlyWeekday = DayOfWeek.MONDAY))
        assertEquals(emptyList<Int>(), slots.map { it.start.dayOfMonth })
    }

    @Test
    fun `每2月_间隔跳月`() {
        // 1-31 起每 2 个月:3-31、5-31、7-31、9-31(不存在跳过)、… 区间内 10 月无、9 月无 → 空
        val slots = series("2026-01-31T09:00", rule(RuleType.MONTHLY, 2, monthlyMode = MonthlyMode.BY_MONTHDAY, monthlyDay = 31))
        assertEquals(emptyList<Int>(), slots.map { it.start.dayOfMonth })
    }

    // —— 每年 ——

    @Test
    fun `每年_生日场景`() {
        val slots = series("2020-10-15T09:00", rule(RuleType.YEARLY))
        assertEquals(1, slots.size)
        assertEquals(2026, slots[0].start.year)
        assertEquals(10, slots[0].start.monthValue)
        assertEquals(15, slots[0].start.dayOfMonth)
    }

    @Test
    fun `每年_2月29日平年跳过`() {
        // 账号裁定:与"日期不存在就跳过"贯通,平年不发生(RFC 5545 同款)
        val slots = series("2024-02-29T09:00", rule(RuleType.YEARLY), rangeStart = LocalDateTime.parse("2025-01-01T00:00"), rangeEnd = LocalDateTime.parse("2028-01-01T00:00"))
        // 2025 平年跳过,2026/2027 平年跳过,区间终点 2028-01-01 之前没有 2-29
        assertEquals(emptyList<Int>(), slots.map { it.start.monthValue })
    }

    // —— 结束条件 ——

    @Test
    fun `结束条件_UNTIL日期含当天`() {
        val slots = series("2026-09-28T09:00", rule(RuleType.DAILY, end = RuleEnd.Until(LocalDate.parse("2026-10-03"))))
        assertEquals(listOf(1, 2, 3), slots.map { it.start.dayOfMonth })
    }

    @Test
    fun `结束条件_COUNT_N次后停止`() {
        val slots = series(
            "2026-09-28T09:00",
            rule(RuleType.DAILY, end = RuleEnd.Count(5)),
            rangeStart = LocalDateTime.parse("2026-09-28T00:00"),
            rangeEnd = LocalDateTime.parse("2026-10-05T00:00"),
        )
        assertEquals(5, slots.size)
    }

    @Test
    fun `结束条件_COUNT_从系列第一次计数_区间外也算`() {
        // 9-28 起共 3 次(9-28/29/30),全部落在区间 [10-01, ...) 之外 → 空
        val slots = series("2026-09-28T09:00", rule(RuleType.DAILY, end = RuleEnd.Count(3)))
        assertEquals(0, slots.size)
    }

    @Test
    fun `结束条件_COUNT_每周多选日按发生次数计`() {
        // 周一+周五,3 次:9-28(一)、10-02(五)、10-05(一) → 区间内 10-02、10-05
        val slots = series("2026-09-28T09:00", rule(RuleType.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), end = RuleEnd.Count(3)))
        assertEquals(listOf(2, 5), slots.map { it.start.dayOfMonth })
    }

    @Test
    fun `结束条件_UNTIL早于系列起点_空`() {
        val slots = series("2026-09-28T09:00", rule(RuleType.DAILY, end = RuleEnd.Until(LocalDate.parse("2026-09-01"))))
        assertEquals(0, slots.size)
    }

    // —— 区间与系列起点 ——

    @Test
    fun `系列起点晚于区间_仅未来次不回填`() {
        val slots = series("2026-10-20T09:00", rule(RuleType.DAILY))
        assertEquals(listOf(20, 21), slots.take(2).map { it.start.dayOfMonth })
    }

    @Test
    fun `全天事件_按整块展开且跨多天时长保留`() {
        // 全天跨 3 天(9-29 00:00 → 10-02 00:00)每周重复:区间内 10-06、10-13、… 各占 3 天
        val slots = series("2026-09-29T00:00", rule(RuleType.WEEKLY, 1, setOf(DayOfWeek.TUESDAY)), Duration.ofDays(3))
        assertTrue(slots.isNotEmpty())
        assertEquals(Duration.ofDays(3), Duration.between(slots[0].start, slots[0].end))
        assertTrue(slots.all { it.start.toLocalTime().toString() == "00:00" })
    }

    @Test
    fun `半开区间_结束贴起点不算重叠`() {
        // 发生 10-01 09:00-10:00,区间从 10-01 10:00 起:10-01 那次结束恰好贴区间起点 → 不算
        val slots = series("2026-09-28T09:00", rule(RuleType.DAILY), rangeStart = LocalDateTime.parse("2026-10-01T10:00"))
        assertEquals(2, slots.first().start.dayOfMonth)
        assertEquals(30, slots.size) // 10-02 .. 10-31
    }

    @Test
    fun `区间为空_返回空`() {
        val slots = series("2026-09-28T09:00", rule(RuleType.DAILY), rangeStart = rangeE, rangeEnd = rangeS)
        assertEquals(0, slots.size)
    }

    @Test
    fun `结果按时间升序`() {
        val slots = series("2026-09-28T09:00", rule(RuleType.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
        assertEquals(slots.map { it.start }.sorted(), slots.map { it.start })
    }
}
