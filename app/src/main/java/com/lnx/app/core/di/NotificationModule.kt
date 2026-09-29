package com.lnx.app.core.di

import com.lnx.app.core.notification.ReminderAlarmSink
import com.lnx.app.core.notification.ReminderScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 闹钟落点的绑定;实现是 [ReminderScheduler](真实 AlarmManager),单测里换成假实现 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    @Singleton
    fun provideReminderAlarmSink(scheduler: ReminderScheduler): ReminderAlarmSink = scheduler
}
