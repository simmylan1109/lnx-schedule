package com.lnx.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
 * 仪器测试的设置存储:**每个用例一个独立文件**。
 *
 * 不用真文件名的话,同一进程里连续跑的用例会各自建一个 DataStore 指向同一个文件,
 * DataStore 会直接抛 "There are multiple DataStores active for the same file"。
 * 顺带的好处:用例之间不会互相带设置(如引导标记),起始状态一律是出厂值。
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [com.lnx.app.core.di.SettingsDataStoreModule::class])
object TestSettingsModule {

    private val counter = AtomicInteger(0)

    @Provides
    @Singleton
    fun provideTestSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        val file = File(context.cacheDir, "test-settings-${counter.incrementAndGet()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { file }
    }
}
