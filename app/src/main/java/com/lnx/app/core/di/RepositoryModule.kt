package com.lnx.app.core.di

import com.lnx.app.core.backup.BackupFiles
import com.lnx.app.core.backup.BackupFileStore
import com.lnx.app.core.backup.BackupRepository
import com.lnx.app.core.data.BackupRepositoryImpl
import com.lnx.app.core.data.EventRepositoryImpl
import com.lnx.app.core.data.SearchRepositoryImpl
import com.lnx.app.core.data.TagRepositoryImpl
import com.lnx.app.core.domain.DefaultOccurrenceExpander
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.OccurrenceExpander
import com.lnx.app.core.domain.TagRepository
import com.lnx.app.core.domain.search.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindBackupFiles(impl: BackupFileStore): BackupFiles

    @Binds
    @Singleton
    abstract fun bindOccurrenceExpander(impl: DefaultOccurrenceExpander): OccurrenceExpander
}
