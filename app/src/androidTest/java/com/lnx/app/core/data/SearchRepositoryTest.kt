package com.lnx.app.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.EventExceptionDao
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventExceptionEntity
import com.lnx.app.core.database.entity.eventExceptionId
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.search.SearchEngine
import com.lnx.app.core.domain.search.SearchRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 搜索的 SQL 那一半(spec §3.9),用真 Room 跑。
 * JVM 单测里的假 DAO 证明不了 LIKE / ESCAPE / IFNULL 写对了 —— 这三样只有真 SQLite 才作数。
 *
 * 每个用例用一个独有的关键词(基于用例名),避免与其他测试类共用同一个库时互相干扰。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SearchRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var dao: EventDao

    @Inject
    lateinit var exceptionDao: EventExceptionDao

    @Inject
    lateinit var search: SearchRepository

    private val now: LocalDateTime = LocalDateTime.parse("2026-09-29T10:00")

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun seed(
        id: String,
        title: String,
        location: String? = null,
        notes: String? = null,
        start: String = "2026-09-30T10:00",
        end: String = "2026-09-30T11:00",
        ruleType: String = "NONE",
        isDeleted: Boolean = false,
    ) = runBlocking {
        val s = LocalDateTime.parse(start)
        dao.upsert(
            EventEntity(
                id = id,
                title = title,
                allDay = false,
                startAt = s.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                endAt = LocalDateTime.parse(end).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                location = location,
                notes = notes,
                colorSlot = 0,
                priority = "P2",
                reminderLeadMinutes = null,
                ruleType = ruleType,
                ruleInterval = 1,
                ruleWeekdays = null,
                ruleMonthlyMode = null,
                ruleMonthlyDay = null,
                ruleMonthlyNth = null,
                ruleMonthlyWeekday = null,
                ruleEndType = null,
                ruleEndDate = null,
                ruleCount = null,
                createdAt = 0L,
                updatedAt = 0L,
                isDeleted = isDeleted,
            ),
        )
    }

    private fun titlesFor(query: String): List<String> =
        runBlocking { search.search(query, now).first().map { it.event.title } }

    @Test
    fun 标题命中() {
        seed("sr-title", "ZQX 季度复盘")
        assertEquals(listOf("ZQX 季度复盘"), titlesFor("ZQX"))
    }

    @Test
    fun 备注命中() {
        seed("sr-notes", "无关标题", notes = "带上 KKW 编号的纪要")
        assertEquals(listOf("无关标题"), titlesFor("KKW"))
    }

    @Test
    fun 地点命中() {
        seed("sr-loc", "另一个标题", location = "在 JJJ 楼三层")
        assertEquals(listOf("另一个标题"), titlesFor("JJJ"))
    }

    @Test
    fun 不区分大小写() {
        seed("sr-case", "Standup Sync")
        assertEquals(listOf("Standup Sync"), titlesFor("standup"))
        assertEquals(listOf("Standup Sync"), titlesFor("SYNC"))
    }

    @Test
    fun 覆盖过去的日期() {
        seed("sr-past", "很久以前的会", start = "2019-05-06T10:00", end = "2019-05-06T11:00")
        assertEquals(listOf("很久以前的会"), titlesFor("很久以前"))
    }

    @Test
    fun 软删除的不出现() {
        seed("sr-del", "已删的会", isDeleted = true)
        assertEquals(emptyList<String>(), titlesFor("已删的会"))
    }

    @Test
    fun 备注与地点为空时不影响其他字段匹配() {
        // 守住可空列:只填标题、备注与地点都留空的事件,按标题必须能搜到
        seed("sr-null", "光秃秃的标题")
        assertEquals(listOf("光秃秃的标题"), titlesFor("光秃秃"))
    }

    @Test
    fun 百分号当普通字符搜() {
        seed("sr-pct", "完成度 100%", start = "2026-09-30T10:00")
        seed("sr-pct-other", "完成度 100% 以上", start = "2026-09-30T12:00", end = "2026-09-30T13:00")
        // 模式串已转义,% 是字面量:只命中含 "100%" 的两条,不会被当成通配符去匹配一切
        assertEquals(2, titlesFor("100%").size)
    }

    @Test
    fun 下划线当普通字符搜() {
        seed("sr-us-a", "标记 a_b 在这里", start = "2026-09-30T10:00")
        seed("sr-us-b", "标记 axb 在这里", start = "2026-09-30T12:00", end = "2026-09-30T13:00")
        assertEquals(listOf("标记 a_b 在这里"), titlesFor("a_b"))
    }

    @Test
    fun 重复事件展示下次发生() {
        seed(
            id = "sr-recur",
            title = "每日站会",
            start = "2020-01-01T09:00",
            end = "2020-01-01T10:00",
            ruleType = RuleType.DAILY.name,
        )
        val result = runBlocking { search.search("每日站会", now).first().single() }
        assertTrue(result.recurring)
        assertEquals(LocalDate.parse("2026-09-30"), result.start.toLocalDate())
    }

    @Test
    fun 空白查询不发查询() {
        assertEquals(emptyList<String>(), titlesFor("   "))
    }

    @Test
    fun 模式串把通配符转义掉() {
        assertEquals("%100\\%%", SearchEngine.likePattern("  100%  "))
    }

    @Test
    fun 取消的那次不算下次发生() {
        seed(
            id = "sr-cancel",
            title = "会被取消的会",
            start = "2026-09-01T09:00",
            end = "2026-09-01T10:00",
            ruleType = RuleType.DAILY.name,
        )
        val cancelled = LocalDate.parse("2026-09-30")
        runBlocking {
            exceptionDao.upsert(
                EventExceptionEntity(
                    id = eventExceptionId("sr-cancel", cancelled),
                    masterEventId = "sr-cancel",
                    originalDate = cancelled.toEpochDay(),
                    isCancelled = true,
                    overrideTitle = null,
                    overrideAllDay = null,
                    overrideStartAt = null,
                    overrideEndAt = null,
                    overrideLocation = null,
                    overrideNotes = null,
                    overrideColorSlot = null,
                    overridePriority = null,
                    overrideReminderLeadMinutes = null,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )
        }
        val result = runBlocking { search.search("会被取消的会", now).first().single() }
        assertEquals(LocalDate.parse("2026-10-01"), result.start.toLocalDate())
    }
}
