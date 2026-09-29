package com.lnx.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.lnx.app.core.data.SettingsRepositoryImpl
import com.lnx.app.core.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DataStore 单独一个模块:仪器测试只需要把"存哪儿"换掉(每个用例一个独立文件,
 * 否则同一进程里多个 DataStore 抢同一个文件会抛 "multiple DataStores active"),
 * 设置读写逻辑本身照旧走被测实现。
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(SETTINGS_FILE)
        }

    private const val SETTINGS_FILE = "lnx_settings"
}

/** 设置仓库绑定 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository = impl
}
