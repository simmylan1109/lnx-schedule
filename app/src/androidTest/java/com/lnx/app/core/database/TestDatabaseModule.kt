package com.lnx.app.core.database

import android.content.Context
import androidx.room.Room
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.TagDao
import com.lnx.app.core.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** 仪器测试用内存库替换真实库:每个测试独立、可预置数据、退出即清空 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LnxDatabase =
        Room.inMemoryDatabaseBuilder(context, LnxDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    fun provideEventDao(db: LnxDatabase): EventDao = db.eventDao()

    @Provides
    fun provideTagDao(db: LnxDatabase): TagDao = db.tagDao()
}
