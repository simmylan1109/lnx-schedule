package com.lnx.app.core.di

import android.content.Context
import androidx.room.Room
import com.lnx.app.core.database.LnxDatabase
import com.lnx.app.core.database.dao.EventDao
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
        Room.databaseBuilder(context, LnxDatabase::class.java, "lnx.db").build()

    @Provides
    fun provideEventDao(db: LnxDatabase): EventDao = db.eventDao()
}
