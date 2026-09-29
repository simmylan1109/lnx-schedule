package com.lnx.app.core.di

import android.content.Context
import androidx.room.Room
import com.lnx.app.core.database.LnxDatabase
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
            // v0.1 开发期:升版本直接重建(正式 Migration 在 M7 打磨时补,见计划 Global Constraints)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideEventDao(db: LnxDatabase): EventDao = db.eventDao()

    @Provides
    fun provideEventExceptionDao(db: LnxDatabase): EventExceptionDao = db.eventExceptionDao()

    @Provides
    fun provideTagDao(db: LnxDatabase): TagDao = db.tagDao()
}
