package com.lnx.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * 仪器测试的设置存储:**每个用例一个独立文件**。
 *
 * 不用真文件名的话,同一进程里连续跑的用例会各自建一个 DataStore 指向同一个文件,
 * DataStore 会直接抛 "There are multiple DataStores active for the same file"。
 * 顺带的好处:用例之间不会互相带设置(如引导标记),起始状态一律是出厂值。
 *
 * M6 起多了一件事:App 首次启动会弹三页引导(spec §3.12),而"出厂值"就是
 * `onboardingDone = false` —— 不预置的话,80 多条仪器测试的首页全被引导页盖住,
 * 什么节点都找不到。所以默认预置"引导已完成";只有 `OnboardingTest` 会把它关掉
 * 来测引导本身(用 @BeforeClass 切,得早于规则启动 Activity)。
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [com.lnx.app.core.di.SettingsDataStoreModule::class])
object TestSettingsModule {

    private val counter = AtomicInteger(0)

    /** 预置"引导已完成";[OnboardingTest] 在 @BeforeClass 里置 false */
    @Volatile
    @JvmStatic
    var seedOnboardingDone = true

    @Provides
    @Singleton
    fun provideTestSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        val file = File(context.cacheDir, "test-settings-${counter.incrementAndGet()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { file }.also { store ->
            if (seedOnboardingDone) {
                // provider 不是 suspend,这里同步写一次;DataStore 内部跑在 IO 上,不会死锁
                runBlocking { store.edit { it[SettingKeys.ONBOARDING_DONE] = "true" } }
            }
        }
    }
}
