package com.lnx.app.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.lnx.app.core.di.SyncDataStoreModule
import com.lnx.app.core.di.SyncStore
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

/**
 * 仪器测试的同步状态存储:**每个用例一个独立文件**,与 [com.lnx.app.core.settings.TestSettingsModule]
 * 同一个理由。
 *
 * A6 整改前 SyncStateStore 在构造器里建 DataStore、写死 `lnx_sync_state`,仪器测试每个用例
 * 各自造一个实例抢同一个文件,DataStore 抛 "multiple DataStores active" 崩掉进程、
 * 整轮测试中断(163 例首跑在 LocaleSwitchTest 崩)。换成每用例独立文件后:
 * 不再抢文件,顺带每个用例的游标/镜像都是干净起始值。
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SyncDataStoreModule::class])
object TestSyncDataStoreModule {

    private val counter = AtomicInteger(0)

    /**
     * 本次**进程**的存储文件前缀。理由同 [com.lnx.app.core.settings.TestSettingsModule]:
     * 只用自增计数的话,后一次运行会复用前一次的文件名,上次中断残留的坏文件会让
     * 本进程第一个用例读到 CorruptionException。
     */
    private val runId = System.currentTimeMillis()

    @Provides
    @Singleton
    @SyncStore
    fun provideTestSyncDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        // 顺手清掉历史遗留的测试文件(真文件 lnx_sync_state 在 filesDir/datastore,
        // 测试图不会去读它,无需清理;这里只管本模块自己留在 cacheDir 的东西)
        context.cacheDir.listFiles { f -> f.name.startsWith("test-sync-state-") }
            ?.forEach { runCatching { it.delete() } }
        val file = File(
            context.cacheDir,
            "test-sync-state-$runId-${counter.incrementAndGet()}.preferences_pb",
        )
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { file }
    }
}
