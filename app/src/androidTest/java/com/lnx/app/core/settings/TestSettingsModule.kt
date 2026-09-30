package com.lnx.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.lnx.app.core.common.LocaleContext
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

    /**
     * 本次**进程**的存储文件前缀。
     *
     * 只用自增计数是不够的:计数每个进程都从 0 开始,后一次运行会复用前一次的文件名。
     * 一旦上次运行被中途打断(写了一半的文件留在 cacheDir),下一次进程的**第一个用例**
     * 就读到损坏文件 —— DataStore 抛 CorruptionException,设置流不发射,界面停在
     * "设置还没到"的兜底分支。实测症状极具误导性:引导测试与语言测试整类全红,
     * 而它们**单独跑**又是绿的(那时还没轮到那个坏文件)。
     */
    private val runId = System.currentTimeMillis()

    /** 预置"引导已完成";[OnboardingTest] 在 @BeforeClass 里置 false */
    @Volatile
    @JvmStatic
    var seedOnboardingDone = true

    /**
     * 预置语言(默认不写 = 出厂"跟随系统")。
     * `LanguagePersistenceTest` 用它模拟"上次设成 English 的用户再次冷启动"。
     */
    @Volatile
    @JvmStatic
    var seedLanguage: String? = null

    @Provides
    @Singleton
    fun provideTestSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        // 换了一份存储 = 换了一个"安装":进程级语言缓存要跟着失效,
        // 否则同一进程里跑的后一个用例会沿用前一个用例读到的语言(终审 P1 回归测试因此假绿/假红)
        LocaleContext.resetForTest()
        // 顺手清掉历史遗留的测试文件:它们是上面那个"损坏文件"问题的源头
        context.cacheDir.listFiles { f -> f.name.startsWith("test-settings-") }
            ?.forEach { runCatching { it.delete() } }
        val file = File(context.cacheDir, "test-settings-$runId-${counter.incrementAndGet()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { file }.also { store ->
            // provider 不是 suspend,这里同步写一次;DataStore 内部跑在 IO 上,不会死锁
            runBlocking {
                store.edit { prefs ->
                    if (seedOnboardingDone) prefs[SettingKeys.ONBOARDING_DONE] = "true"
                    seedLanguage?.let { prefs[SettingKeys.LANGUAGE] = it }
                }
            }
        }
    }
}
