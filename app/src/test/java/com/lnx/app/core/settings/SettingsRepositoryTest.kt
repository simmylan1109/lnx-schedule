package com.lnx.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.lnx.app.core.data.SettingsRepositoryImpl
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 设置仓库(spec §3.11 / §3.12):出厂值、写入读回、脏数据兜底。
 *
 * 用真实的 DataStore(临时目录)而不是假件 —— 要验的就是"键名/类型/读回"这一层,
 * 假件会把 key 拼错、类型转换写错这类问题一起放过去。
 */
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tmp.newFolder(), "lnx-settings.preferences_pb")
        }
        repo = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `什么都没写过时返回出厂值`() = runTest {
        val s = repo.settings.first()
        assertEquals(ThemeSlot.MATERIAL_YOU, s.themeSlot)
        assertEquals(DarkMode.FOLLOW_SYSTEM, s.darkMode)
        assertEquals(15, s.reminderLeadMinutes)
        assertEquals(false, s.dndEnabled)
        assertEquals(22 * 60, s.dndStartMinute)
        assertEquals(8 * 60, s.dndEndMinute)
        assertEquals(true, s.weekStartMonday)
        assertEquals("system", s.language)
        assertEquals(false, s.onboardingDone)
    }

    @Test
    fun `写入的每一项都能读回`() = runTest {
        repo.setThemeSlot(ThemeSlot.SERENE)
        repo.setDarkMode(DarkMode.DARK)
        repo.setReminderLead(30)
        repo.setDnd(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60)
        repo.setWeekStartMonday(false)
        repo.setLanguage("en")
        repo.setOnboardingDone()

        val s = repo.settings.first()
        assertEquals(ThemeSlot.SERENE, s.themeSlot)
        assertEquals(DarkMode.DARK, s.darkMode)
        assertEquals(30, s.reminderLeadMinutes)
        assertTrue(s.dndEnabled)
        assertEquals(23 * 60, s.dndStartMinute)
        assertEquals(7 * 60, s.dndEndMinute)
        assertEquals(false, s.weekStartMonday)
        assertEquals("en", s.language)
        assertTrue(s.onboardingDone)
    }

    @Test
    fun `提醒可以设为不提醒_存的是null而不是0`() = runTest {
        repo.setReminderLead(15)
        repo.setReminderLead(null)

        assertNull("不提醒必须存成 null:0 会被当成「提前 0 分钟」立即触发", repo.settings.first().reminderLeadMinutes)
    }

    @Test
    fun `脏数据回落出厂值而不是崩`() = runTest {
        // 模拟别的版本/手改写进去的坏值:主题名不认识、语言串不认识
        dataStore.updateData { it.toMutablePreferences().apply {
            set(SettingKeys.THEME_SLOT, "不存在的槽位")
            set(SettingKeys.LANGUAGE, "klingon")
            set(SettingKeys.REMINDER_LEAD, "abc")
        } }

        val s = repo.settings.first()
        assertEquals(ThemeSlot.MATERIAL_YOU, s.themeSlot)
        assertEquals("system", s.language)
        assertEquals(15, s.reminderLeadMinutes)
    }

    @Test
    fun `负数或越界的提前量按出厂值处理`() = runTest {
        repo.setReminderLead(-5)
        assertEquals(15, repo.settings.first().reminderLeadMinutes)
    }
}
