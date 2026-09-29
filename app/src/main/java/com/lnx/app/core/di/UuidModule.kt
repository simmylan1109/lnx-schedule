package com.lnx.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton

/**
 * 新 id 生成器(函数类型,可被 Hilt 注入)。
 * RecurrenceEditHandler / 单测都要造新 UUID;测试直接构造 handler 时用具名参数换掉。
 */
@Module
@InstallIn(SingletonComponent::class)
object UuidModule {
    @Provides
    @Singleton
    fun provideNewId(): () -> String = { UUID.randomUUID().toString() }
}
