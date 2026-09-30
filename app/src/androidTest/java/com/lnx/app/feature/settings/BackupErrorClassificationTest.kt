package com.lnx.app.feature.settings

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.backup.Backup
import com.lnx.app.core.backup.BackupCodec
import com.lnx.app.core.backup.BackupFiles
import com.lnx.app.core.backup.BackupRepository
import com.lnx.app.core.backup.ImportMode
import com.lnx.app.core.backup.ImportResult
import com.lnx.app.core.backup.ImportSummary
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.notification.ReminderAlarmSink
import com.lnx.app.core.notification.ReminderPlanner
import com.lnx.app.core.notification.ScheduledReminder
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 备份的错误分类(M8 T3,终审 P2-1:这个分类当时零覆盖)。
 *
 * 分类的意义就一句话:**别把"本机数据库出问题"说成"文件有问题"**。
 * 两种混在一起报,用户和排查的人都会往错的方向找。
 *
 * 放仪器测试而不是 JVM 单测:`Uri` 是 Android 类型,JVM 单测里 `Uri.parse` 是没实现的桩
 * (会抛 "not mocked")。为了它给整个 JVM 套件开 `isReturnDefaultValues` 不划算 ——
 * 那个开关会让别的用例里"忘了 mock 的 Android 调用"悄悄返回默认值,等于把真错误藏起来。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BackupErrorClassificationTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // —— 假实现 ——

    private class Files(
        private val writeError: Throwable? = null,
        private val readError: Throwable? = null,
        private val content: String = "{}",
    ) : BackupFiles {
        var written: String? = null

        override suspend fun write(fileName: String, text: String): Uri {
            writeError?.let { throw it }
            written = text
            return Uri.parse("content://test/$fileName")
        }

        override suspend fun read(uri: Uri): String {
            readError?.let { throw it }
            return content
        }
    }

    private class Repo(
        private val snapshotError: Throwable? = null,
        private val summarizeError: Throwable? = null,
        private val importError: Throwable? = null,
    ) : BackupRepository {
        override suspend fun snapshot(now: Long): Backup {
            snapshotError?.let { throw it }
            return Backup()
        }

        override suspend fun currentEventCount(): Int = 0

        override suspend fun summarize(backup: Backup, mode: ImportMode): ImportSummary {
            summarizeError?.let { throw it }
            return ImportSummary(0, 0, 0, 0)
        }

        override suspend fun import(backup: Backup, mode: ImportMode): ImportResult {
            importError?.let { throw it }
            return ImportResult(0, 0, 0, 0)
        }
    }

    private fun vm(files: BackupFiles, repo: BackupRepository) = BackupViewModel(
        repository = repo,
        codec = BackupCodec(),
        files = files,
        // 提醒排期与本测试的断言无关,但导入路径上确实会被调一次
        reminderPlanner = ReminderPlanner(NoEventsRepository(), NoOpAlarmSink),
    )

    // —— 导出侧 ——

    @Test
    fun 导出_读库失败报DATABASE而不是读不出文件() = runTest {
        val vm = vm(Files(), Repo(snapshotError = IllegalStateException("db gone")))

        vm.export { _, _ -> }

        assertEquals(BackupError.DATABASE, vm.state.value.error)
    }

    @Test
    fun 导出_写文件失败报FILE_IO() = runTest {
        val vm = vm(Files(writeError = java.io.IOException("read-only")), Repo())

        vm.export { _, _ -> }

        assertEquals(BackupError.FILE_IO, vm.state.value.error)
    }

    @Test
    fun 导出成功给文件名并把内容写出去() = runTest {
        val files = Files()
        val vm = vm(files, Repo())
        var name = ""

        vm.export { _, n -> name = n }

        assertNull(vm.state.value.error)
        assertEquals("lnx-backup-20260930.json", name)
        // 内容确实落到了文件里,而且带着自我标识字段
        assertTrue(files.written?.contains("\"format\"") == true)
    }

    // —— 导入侧 ——

    @Test
    fun 导入_读不出文件报FILE_IO() = runTest {
        val vm = vm(Files(readError = java.io.FileNotFoundException("nope")), Repo())

        vm.onFilePicked(Uri.parse("content://x"))

        assertEquals(BackupError.FILE_IO, vm.state.value.error)
    }

    @Test
    fun 导入_不是json报CORRUPTED() = runTest {
        val vm = vm(Files(content = "{ 这不是 json"), Repo())

        vm.onFilePicked(Uri.parse("content://x"))

        assertEquals(BackupError.CORRUPTED, vm.state.value.error)
    }

    @Test
    fun 导入_别的App的json报NOT_LNX_BACKUP() = runTest {
        val vm = vm(Files(content = """{"foo":1}"""), Repo())

        vm.onFilePicked(Uri.parse("content://x"))

        assertEquals(BackupError.NOT_LNX_BACKUP, vm.state.value.error)
    }

    @Test
    fun 导入_摘要计算失败报DATABASE而不是文件问题() = runTest {
        val files = Files(content = """{"format":"lnx-backup","version":1}""")
        val vm = vm(files, Repo(summarizeError = IllegalStateException("db locked")))

        vm.onFilePicked(Uri.parse("content://x"))

        assertEquals(BackupError.DATABASE, vm.state.value.error)
    }

    @Test
    fun 导入_落库失败报DATABASE() = runTest {
        val files = Files(content = """{"format":"lnx-backup","version":1}""")
        val vm = vm(files, Repo(importError = IllegalStateException("constraint failed")))

        vm.onFilePicked(Uri.parse("content://x"))
        vm.chooseMerge()

        assertEquals(BackupError.DATABASE, vm.state.value.error)
    }

    // —— 只为把 ReminderPlanner 拼出来,语义无关 ——

    private class NoEventsRepository : EventRepository {
        override fun observeOccurrences(
            start: LocalDateTime,
            end: LocalDateTime,
        ): Flow<List<Occurrence>> = emptyFlow()

        override fun observeEvents(start: LocalDateTime, end: LocalDateTime): Flow<List<Event>> = emptyFlow()
        override suspend fun getEvent(id: String): Event? = null
        override suspend fun save(event: Event) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun upsertException(exception: EventException) = Unit
        override suspend fun cancelOccurrence(masterId: String, originalDate: LocalDate) = Unit
        override suspend fun deleteExceptionsFor(masterId: String) = Unit
        override suspend fun deleteExceptionsFrom(masterId: String, from: LocalDate) = Unit
    }

    private object NoOpAlarmSink : ReminderAlarmSink {
        override fun schedule(reminders: List<ScheduledReminder>, continueAt: LocalDateTime?) = Unit
        override fun cancelAll() = Unit
    }
}
