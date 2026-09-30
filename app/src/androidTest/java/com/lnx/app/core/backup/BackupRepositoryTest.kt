package com.lnx.app.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.EventExceptionDao
import com.lnx.app.core.database.dao.TagDao
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventTagCrossRef
import com.lnx.app.core.database.entity.TagEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 备份导入导出(spec §3.13),用真 Room 跑。
 *
 * 合并/覆盖的判据只在真库上验才算数:唯一索引、`@Upsert` 的插入-更新分路、
 * 事务回滚这些行为假 DAO 一个都模拟不出来。
 *
 * 每个用例开头清库;用例之间互不依赖(仪器测试共用一个 `lnx.db`)。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var eventDao: EventDao

    @Inject
    lateinit var tagDao: TagDao

    @Inject
    lateinit var exceptionDao: EventExceptionDao

    @Inject
    lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            tagDao.clearAllRefs()
            exceptionDao.clearAll()
            eventDao.clearAll()
            tagDao.clearAll()
        }
    }

    private fun event(
        id: String,
        title: String = id,
        updatedAt: Long = 0L,
        isDeleted: Boolean = false,
    ) = EventEntity(
        id = id,
        title = title,
        allDay = false,
        startAt = 1_757_000_000_000,
        endAt = 1_757_003_600_000,
        location = null,
        notes = null,
        colorSlot = 0,
        priority = "P2",
        reminderLeadMinutes = null,
        ruleType = "NONE",
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
        updatedAt = updatedAt,
        isDeleted = isDeleted,
    )

    @Test
    fun 导出能带走事件标签关联() = runBlocking {
        eventDao.upsert(event("e1"))
        tagDao.upsert(TagEntity(id = "t1", name = "工作", colorSlot = 1, 0L, 0L, false))
        tagDao.insertCrossRef(EventTagCrossRef("e1", "t1"))

        val snap = repository.snapshot(0L)
        assertEquals(listOf("e1"), snap.events.map { it.id })
        assertEquals(listOf("t1"), snap.tags.map { it.id })
        assertEquals(listOf(BackupEventTag("e1", "t1")), snap.eventTags)
    }

    @Test
    fun 导出带走软删墓碑() = runBlocking {
        eventDao.upsert(event("gone", isDeleted = true))
        val snap = repository.snapshot(0L)
        assertEquals(true, snap.events.single { it.id == "gone" }.isDeleted)
    }

    @Test
    fun 合并_新id加入_已存在且不新的跳过() = runBlocking {
        eventDao.upsert(event("old", title = "库里的版本", updatedAt = 100))
        val backup = Backup(
            events = listOf(
                event("old", title = "备份里的版本", updatedAt = 100).toBackup(),
                event("fresh", title = "新的", updatedAt = 100).toBackup(),
            ),
        )

        val result = repository.import(backup, ImportMode.MERGE)
        assertEquals(1, result.eventsAdded)
        assertEquals(1, result.eventsSkipped)
        assertEquals("库里的版本", eventDao.getById("old")?.title)
        assertEquals("新的", eventDao.getById("fresh")?.title)
    }

    @Test
    fun 合并_同id冲突保留updatedAt较新的一方() = runBlocking {
        eventDao.upsert(event("x", title = "旧", updatedAt = 100))
        val backup = Backup(events = listOf(event("x", title = "新", updatedAt = 200).toBackup()))

        val result = repository.import(backup, ImportMode.MERGE)
        assertEquals(1, result.eventsReplaced)
        assertEquals("新", eventDao.getById("x")?.title)
    }

    @Test
    fun 合并_备份比库里旧则不覆盖() = runBlocking {
        eventDao.upsert(event("x", title = "新", updatedAt = 200))
        val backup = Backup(events = listOf(event("x", title = "旧", updatedAt = 100).toBackup()))

        repository.import(backup, ImportMode.MERGE)
        assertEquals("新", eventDao.getById("x")?.title)
    }

    @Test
    fun 合并_不会重复插同一条关联() = runBlocking {
        eventDao.upsert(event("e1"))
        tagDao.upsert(TagEntity(id = "t1", name = "工作", colorSlot = 1, 0L, 0L, false))
        tagDao.insertCrossRef(EventTagCrossRef("e1", "t1"))
        val backup = Backup(
            events = listOf(event("e1").toBackup()),
            tags = listOf(BackupTag("t1", "工作", 1)),
            eventTags = listOf(BackupEventTag("e1", "t1")),
        )

        // 主键是 (eventId, tagId),重复插入会撞唯一索引;跑通即证明去重生效
        repository.import(backup, ImportMode.MERGE)
        assertEquals(1, tagDao.allEventTags().count { it.eventId == "e1" && it.tagId == "t1" })
    }

    @Test
    fun 合并_指向不存在事件的关联被丢掉() = runBlocking {
        val backup = Backup(
            tags = listOf(BackupTag("t1", "工作", 1)),
            eventTags = listOf(BackupEventTag("不存在的", "t1")),
        )
        repository.import(backup, ImportMode.MERGE)
        assertTrue(tagDao.allEventTags().isEmpty())
    }

    @Test
    fun 覆盖_清空现有数据() = runBlocking {
        eventDao.upsert(event("old1"))
        eventDao.upsert(event("old2"))
        val backup = Backup(events = listOf(event("new1").toBackup()))

        repository.import(backup, ImportMode.OVERWRITE)
        assertEquals(listOf("new1"), eventDao.allOnce().map { it.id })
    }

    @Test
    fun 覆盖_墓碑也一起还原() = runBlocking {
        val backup = Backup(
            events = listOf(
                event("gone", isDeleted = true).toBackup(),
                event("live").toBackup(),
            ),
        )
        repository.import(backup, ImportMode.OVERWRITE)
        assertEquals(1, repository.currentEventCount())
        assertEquals(2, eventDao.allOnce().size)
    }

    @Test
    fun 摘要数的是未删事件() = runBlocking {
        eventDao.upsert(event("a"))
        eventDao.upsert(event("b", isDeleted = true))
        val backup = Backup(
            events = listOf(event("c").toBackup(), event("d", isDeleted = true).toBackup()),
            tags = listOf(BackupTag("t1", "工作", 1), BackupTag("t2", "已删", 1, isDeleted = true)),
        )
        val summary = repository.summarize(backup, ImportMode.MERGE)
        assertEquals(1, summary.eventCount)
        assertEquals(1, summary.tagCount)
        assertEquals(1, summary.currentCount)
    }

    @Test
    fun 导出再导入_数据完全一致() = runBlocking {
        eventDao.upsert(event("e1", title = "复盘"))
        tagDao.upsert(TagEntity(id = "t1", name = "工作", colorSlot = 1, 0L, 0L, false))
        tagDao.insertCrossRef(EventTagCrossRef("e1", "t1"))
        val exported = repository.snapshot(42L)

        // 换个库状态:清空后覆盖导入,应当回到导出时的样子
        eventDao.clearAll()
        tagDao.clearAllRefs()
        tagDao.clearAll()
        repository.import(exported, ImportMode.OVERWRITE)

        val after = repository.snapshot(0L)
        assertEquals(exported.events, after.events)
        assertEquals(exported.tags, after.tags)
        assertEquals(exported.eventTags, after.eventTags)
    }

    @Test
    fun 空库导出得到空快照() = runBlocking {
        val snap = repository.snapshot(0L)
        assertEquals(0, snap.events.size)
        assertEquals(0, repository.currentEventCount())
    }

    // —— 例外(重复事件的命门)——
    // 终审指出例外链路此前零真库用例:覆盖导入把例外清掉再灌回来,
    // 这一步错了,重复事件的"仅本次修改/取消"会整段失效。

    private fun exception(
        masterId: String,
        epochDay: Long,
        cancelled: Boolean = true,
        updatedAt: Long = 0L,
    ) = com.lnx.app.core.database.entity.EventExceptionEntity(
        id = com.lnx.app.core.database.entity.eventExceptionId(
            masterId, java.time.LocalDate.ofEpochDay(epochDay),
        ),
        masterEventId = masterId,
        originalDate = epochDay,
        isCancelled = cancelled,
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
        updatedAt = updatedAt,
    )

    @Test
    fun 例外跟着导出也能跟着回来() = runBlocking {
        eventDao.upsert(event("m1"))
        exceptionDao.upsert(exception("m1", 20_000))
        val exported = repository.snapshot(0L)
        assertEquals(1, exported.exceptions.size)

        eventDao.clearAll()
        exceptionDao.clearAll()
        repository.import(exported, ImportMode.OVERWRITE)

        val restored = exceptionDao.getFor("m1", 20_000)
        assertEquals(true, restored?.isCancelled)
    }

    @Test
    fun 覆盖导入把库里的旧例外清干净() = runBlocking {
        eventDao.upsert(event("m1"))
        exceptionDao.upsert(exception("m1", 19_000))
        val backup = Backup(events = listOf(event("m1").toBackup()))

        repository.import(backup, ImportMode.OVERWRITE)

        // 备份里没有例外 → 导入后也不该有,否则被取消的那次会复活
        assertTrue(exceptionDao.allOnce().isEmpty())
    }

    @Test
    fun 合并_例外的updatedAt较新者赢() = runBlocking {
        exceptionDao.upsert(exception("m1", 20_000, cancelled = false, updatedAt = 100))
        val backup = Backup(
            events = listOf(event("m1").toBackup()),
            exceptions = listOf(
                BackupException(
                    id = com.lnx.app.core.database.entity.eventExceptionId(
                        "m1", java.time.LocalDate.ofEpochDay(20_000),
                    ),
                    masterEventId = "m1",
                    originalDate = 20_000,
                    isCancelled = true,
                    updatedAt = 200,
                ),
            ),
        )
        repository.import(backup, ImportMode.MERGE)
        assertEquals(true, exceptionDao.getFor("m1", 20_000)?.isCancelled)
    }

    @Test
    fun 合并_同名不同id的标签沿用库里的那条() = runBlocking {
        // 终审 P1-2:两台设备各自建了"工作",id 不同。直接 upsert 撞 name 唯一索引,
        // @Upsert 吞异常后按新 id UPDATE 影响 0 行 —— 标签消失但关联插进去了(幽灵关联)
        eventDao.upsert(event("e1"))
        tagDao.upsert(TagEntity(id = "local-1", name = "工作", colorSlot = 1, 0L, 0L, false))
        val backup = Backup(
            events = listOf(event("e1").toBackup()),
            tags = listOf(BackupTag("remote-9", "工作", 1)),
            eventTags = listOf(BackupEventTag("e1", "remote-9")),
        )

        val result = repository.import(backup, ImportMode.MERGE)

        // 标签没有凭空多出一条,关联重映射到库里那条 id 上,可查、不悬空
        assertEquals(1, tagDao.allOnce().count { it.name == "工作" })
        assertEquals(
            listOf(EventTagCrossRef("e1", "local-1")),
            tagDao.allEventTags(),
        )
        assertEquals(0, result.tagsAdded)
    }

    @Test
    fun 合并_同id但名字被别的行占用时不覆盖() = runBlocking {
        tagDao.upsert(TagEntity(id = "a", name = "工作", colorSlot = 1, 0L, 0L, false))
        tagDao.upsert(TagEntity(id = "b", name = "生活", colorSlot = 2, 0L, 0L, false))
        val backup = Backup(
            tags = listOf(BackupTag("b", "工作", 1, updatedAt = 999)),
        )
        repository.import(backup, ImportMode.MERGE)
        // b 想改名成"工作",但"工作"已被 a 占用:宁可不动也不能撞唯一索引
        assertEquals("生活", tagDao.getById("b")?.name)
    }
}

private fun EventEntity.toBackup() = BackupEvent(
    id = id,
    title = title,
    allDay = allDay,
    startAt = startAt,
    endAt = endAt,
    location = location,
    notes = notes,
    colorSlot = colorSlot,
    priority = priority,
    reminderLeadMinutes = reminderLeadMinutes,
    ruleType = ruleType,
    ruleInterval = ruleInterval,
    ruleWeekdays = ruleWeekdays,
    ruleMonthlyMode = ruleMonthlyMode,
    ruleMonthlyDay = ruleMonthlyDay,
    ruleMonthlyNth = ruleMonthlyNth,
    ruleMonthlyWeekday = ruleMonthlyWeekday,
    ruleEndType = ruleEndType,
    ruleEndDate = ruleEndDate,
    ruleCount = ruleCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)
