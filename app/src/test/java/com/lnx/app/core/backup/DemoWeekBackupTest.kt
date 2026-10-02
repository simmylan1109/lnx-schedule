package com.lnx.app.core.backup

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.recurrence.RecurrenceEngine
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标准验收数据集 `docs/acceptance/v0.2/demo-week-backup.json` 的合同(v0.2 补欠账④)。
 *
 * **它为什么存在**:周视图"所有天都画在同一列"那个严重 bug 活了九个里程碑、155 条测试全绿,
 * 根因是验收截图的数据**恰好都是单日事件** —— 单日时块画在哪列都"对"。教训:验收数据必须
 * 覆盖"多条 + 跨维度"的组合。这份文件就是固化下来的验收数据,特点:
 *
 * - 全部"每周重复、永不结束",锚在过去 —— **任何一周**打开都有内容,数据集永不过期
 * - 一周 7 天全有日程(含周三同日重叠 → 车道并排、周日全天 → 全天横条)
 *
 * 这条测试保证它**永远不会悄悄坏掉**:格式合法、覆盖维度齐全。人工走查前在
 * 「设置 → 导入 → 合并」一次即可(见 docs/acceptance/v0.2/README.md)。
 */
class DemoWeekBackupTest {

    private fun demoFile(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(5) {
            val candidate = File(dir, "docs/acceptance/v0.2/demo-week-backup.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("找不到 demo-week-backup.json(从 ${System.getProperty("user.dir")} 向上找)")
    }

    private val backup: Backup by lazy {
        val parsed = BackupCodec().decode(demoFile().readText())
        assertTrue("演示数据必须是合法的 lnx 备份,实际:$parsed", parsed is BackupParse.Ok)
        (parsed as BackupParse.Ok).backup
    }

    @Test
    fun `格式标识与版本号在文件里`() {
        assertEquals(Backup.FORMAT, backup.format)
        assertEquals(Backup.FORMAT_VERSION, backup.version)
    }

    @Test
    fun `覆盖维度_六条事件两个标签五条关联`() {
        assertEquals(6, backup.events.size)
        assertEquals(2, backup.tags.size)
        assertEquals(5, backup.eventTags.size)
    }

    @Test
    fun `覆盖维度_一周七天全有日程`() {
        val weekdays = backup.events
            .mapNotNull { it.ruleWeekdays }
            .flatMap { it.split(",") }
            .map { it.trim().toInt() }
            .toSet()
        assertEquals("演示数据应覆盖周一到周日", (1..7).toSet(), weekdays)
    }

    @Test
    fun `覆盖维度_同日有重叠对_车道布局有活证据`() {
        val review = backup.events.first { it.id == "demo-review" }
        val sync = backup.events.first { it.id == "demo-sync" }
        assertEquals("两条都排周三", review.ruleWeekdays, sync.ruleWeekdays)
        // 时间区间相交(半开区间:start < 对方 end 且 end > 对方 start)
        assertTrue(
            "需求评审与项目同步必须时间重叠,否则周三列的车道并排没有被验收",
            review.startAt < sync.endAt && review.endAt > sync.startAt,
        )
    }

    @Test
    fun `覆盖维度_恰好一条全天事件且排在周日`() {
        val allDay = backup.events.filter { it.allDay }
        assertEquals(1, allDay.size)
        assertEquals("7", allDay.single().ruleWeekdays)
        // 全天的结束必须是起始次日 00:00(排他),否则横条长度算错
        val zone = ZoneId.systemDefault()
        val start = java.time.Instant.ofEpochMilli(allDay.single().startAt).atZone(zone).toLocalDate()
        val end = java.time.Instant.ofEpochMilli(allDay.single().endAt).atZone(zone).toLocalDate()
        assertEquals(start.plusDays(1), end)
    }

    @Test
    fun `数据集永不过期_全部每周重复且永不结束`() {
        backup.events.forEach { e ->
            assertEquals("全部事件必须 WEEKLY:${e.id}", "WEEKLY", e.ruleType)
            assertEquals(1, e.ruleInterval)
            assertEquals("永不结束,数据集才不会过期:${e.id}", null, e.ruleEndType)
            assertTrue(e.isDeleted.not())
        }
    }

    @Test
    fun `字段卫生_标题色位优先级都合法`() {
        val priorities = setOf("P0", "P1", "P2", "P3")
        backup.events.forEach { e ->
            assertTrue("标题不能空:${e.id}", e.title.isNotBlank())
            assertTrue("色位 0..7:${e.id}", e.colorSlot in 0..7)
            assertTrue("优先级非法:${e.id}=${e.priority}", e.priority in priorities)
        }
    }

    @Test
    fun `用真的展开器跑一遍_任意一周每天都有发生`() {
        // 与界面同一套展开逻辑(RecurrenceEngine),挑锚点后第三周整周验证
        val zone = ZoneId.systemDefault()
        backup.events.forEach { e ->
            val start = java.time.Instant.ofEpochMilli(e.startAt).atZone(zone).toLocalDateTime()
            val end = java.time.Instant.ofEpochMilli(e.endAt).atZone(zone).toLocalDateTime()
            val rule = EventRule(
                type = RuleType.WEEKLY,
                interval = e.ruleInterval,
                weekdays = e.ruleWeekdays.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
                    .mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet(),
                end = RuleEnd.Never,
            )
            val event = Event(
                id = e.id,
                title = e.title,
                allDay = e.allDay,
                start = start,
                end = end,
                location = e.location,
                notes = e.notes,
                colorSlot = e.colorSlot,
                priority = Priority.entries.first { it.name == e.priority },
                reminderLeadMinutes = e.reminderLeadMinutes,
                rule = rule,
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
            )
            val slots = RecurrenceEngine.expand(
                rule,
                start,
                Duration.between(start, end),
                LocalDate.of(2026, 10, 12).atStartOfDay(), // 锚点周(9/28)后的第三周周一
                LocalDate.of(2026, 10, 19).atStartOfDay(),
            )
            assertTrue("该事件在这一周至少发生一次:${e.id}", slots.isNotEmpty())
        }
    }
}
