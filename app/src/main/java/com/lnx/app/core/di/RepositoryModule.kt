package com.lnx.app.core.di

import com.lnx.app.core.data.EventRepositoryImpl
import com.lnx.app.core.data.TagRepositoryImpl
import com.lnx.app.core.domain.BasicOccurrenceExpander
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.OccurrenceExpander
import com.lnx.app.core.domain.TagRepository
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
    abstract fun bindOccurrenceExpander(impl: BasicOccurrenceExpander): OccurrenceExpander
}
