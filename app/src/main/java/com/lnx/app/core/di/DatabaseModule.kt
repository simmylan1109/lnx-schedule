package com.lnx.app.core.di

import android.content.Context
import androidx.room.Room
import com.lnx.app.core.database.LnxDatabase
import com.lnx.app.core.database.LnxMigrations
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.EventExceptionDao
import com.lnx.app.core.database.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LnxDatabase =
        Room.databaseBuilder(context, LnxDatabase::class.java, "lnx.db")
            // M8 起是正式迁移(见 LnxMigrations):升版本**不再清库**。
            // 这里刻意不挂 fallbackToDestructiveMigration —— 万一漏了某条迁移路径,
            // 宁可当场抛异常让人发现,也不能悄悄把用户的日程清空。
            .addMigrations(*LnxMigrations.ALL)
            .build()

    @Provides
    fun provideEventDao(db: LnxDatabase): EventDao = db.eventDao()

    @Provides
    fun provideEventExceptionDao(db: LnxDatabase): EventExceptionDao = db.eventExceptionDao()

    @Provides
    fun provideTagDao(db: LnxDatabase): TagDao = db.tagDao()
}
