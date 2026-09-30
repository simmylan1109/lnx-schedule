package com.lnx.app.core.backup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份文件的编解码(spec §3.13)。纯 JVM,不含 Android 依赖。
 * 重点是"坏文件不许被当备份读进去" —— 读错了会直接清库,代价太大。
 */
class BackupCodecTest {

    private val codec = BackupCodec()

    private val sample = Backup(
        exportedAt = 1_700_000_000_000,
        events = listOf(
            BackupEvent(
                id = "e1",
                title = "季度复盘",
                allDay = false,
                startAt = 1_700_000_000_000,
                endAt = 1_700_003_600_000,
                location = "三层",
                notes = "带上纪要",
                colorSlot = 3,
                priority = "P1",
                reminderLeadMinutes = 15,
                ruleType = "WEEKLY",
                ruleInterval = 1,
                ruleWeekdays = "1,3",
                createdAt = 1_699_000_000_000,
                updatedAt = 1_699_900_000_000,
            ),
        ),
        exceptions = listOf(
            BackupException(
                id = "e1:20000",
                masterEventId = "e1",
                originalDate = 20_000,
                isCancelled = true,
            ),
        ),
        tags = listOf(BackupTag(id = "t1", name = "工作", colorSlot = 2)),
        eventTags = listOf(BackupEventTag(eventId = "e1", tagId = "t1")),
    )

    @Test
    fun `导出再导入_内容一字不差`() {
        val parsed = codec.decode(codec.encode(sample))
        assertTrue(parsed is BackupParse.Ok)
        assertEquals(sample, (parsed as BackupParse.Ok).backup)
    }

    @Test
    fun `软删墓碑能原样往返`() {
        // 墓碑丢了 = 换机后删掉的日程复活(spec §3.13 的覆盖导入要能还原删除态)
        val withTomb = sample.copy(
            events = sample.events + sample.events.first().copy(id = "e2", isDeleted = true),
        )
        val parsed = codec.decode(codec.encode(withTomb)) as BackupParse.Ok
        assertEquals(true, parsed.backup.events.first { it.id == "e2" }.isDeleted)
    }

    @Test
    fun `认不出的字段被忽略_新版本文件老版本读得进`() {
        val json = """
            {"format":"lnx-backup","version":1,"exportedAt":0,
             "events":[],"exceptions":[],"tags":[],"eventTags":[],
             "someFutureField":{"a":1}}
        """.trimIndent()
        assertTrue(codec.decode(json) is BackupParse.Ok)
    }

    @Test
    fun `不是 lnx 备份会被挡下`() {
        val parsed = codec.decode("""{"format":"别的东西","version":1}""")
        assertEquals(BackupParse.NotLnxBackup, parsed)
    }

    @Test
    fun `版本比当前新会被挡下`() {
        val json = """{"format":"lnx-backup","version":99}"""
        val parsed = codec.decode(json)
        assertTrue(parsed is BackupParse.UnsupportedVersion)
        assertEquals(99, (parsed as BackupParse.UnsupportedVersion).version)
    }

    @Test
    fun `坏文件报损坏而不是抛异常`() {
        val parsed = codec.decode("{ 这不是 json")
        assertTrue(parsed is BackupParse.Corrupted)
    }

    @Test
    fun `format与version真的写进了文件`() {
        // 实机走查踩过:编码器开了 encodeDefaults=false,而这两个字段恰好等于默认值,
        // 结果导出的文件里一个标记都没有。往返测试 decode(encode(x))==x 照样通过,查不出来,
        // 只能直接断言文件里有没有这两个键。
        val json = codec.encode(Backup())
        assertTrue("文件里必须有 format 标识", json.contains("\"format\""))
        assertTrue(json.contains(Backup.FORMAT))
        assertTrue("文件里必须有 format 版本号", json.contains("\"version\""))
    }

    @Test
    fun `空备份也能被认出是 lnx 备份`() {
        // 反面:只靠"形状对得上"的 json 也会被放行,那 format 标记就白写了
        assertTrue(codec.decode("""{"events":[]}""") is BackupParse.Ok)
        assertEquals(BackupParse.NotLnxBackup, codec.decode("""{"format":"other","version":1}"""))
    }

    @Test
    fun `文件名是 lnx-backup-YYYYMMDD_json`() {
        assertEquals("lnx-backup-20260930.json", codec.fileName(LocalDate.of(2026, 9, 30)))
        assertEquals("lnx-backup-20260101.json", codec.fileName(LocalDate.of(2026, 1, 1)))
    }
}
