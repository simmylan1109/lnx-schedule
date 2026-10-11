package com.lnx.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 同步状态 DataStore 单独一个模块(v0.3 A6 整改):和设置侧的 [SettingsDataStoreModule]
 * 同一个理由——仪器测试要把"存哪儿"换成每用例独立文件。
 *
 * 此前 SyncStateStore 在自己的构造器里建 DataStore、写死文件名,仪器测试每个用例都是
 * 新的 Hilt 组件、各自造一个实例抢同一个文件,DataStore 直接抛
 * "There are multiple DataStores active for the same file" 崩掉进程、整轮测试中断
 * (A6 在 x64 模拟器首跑 163 例时踩中,见 LocaleSwitchTest)。
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncDataStoreModule {

    @Provides
    @Singleton
    @SyncStore
    fun provideSyncDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(FILE_NAME)
        }

    private const val FILE_NAME = "lnx_sync_state"
}
