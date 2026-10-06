package com.lnx.app.core.di

import com.lnx.app.core.network.LnxApiClient
import com.lnx.app.core.network.SyncApi
import com.lnx.app.core.sync.LocalSyncStore
import com.lnx.app.core.sync.RoomSyncStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 同步层的接口绑定(v0.3 A4):引擎只认窄口,测试换假件 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindSyncApi(impl: LnxApiClient): SyncApi

    @Binds
    @Singleton
    abstract fun bindLocalSyncStore(impl: RoomSyncStore): LocalSyncStore
}