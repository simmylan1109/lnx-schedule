package com.lnx.app.core.notification

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 接收器取依赖的入口。
 *
 * **不用 `@AndroidEntryPoint` 字段注入**:它会在 `onReceive` 一进来就 inject,
 * 组件没就绪时直接抛 `IllegalStateException` 把**整个进程**带崩
 * (实测:仪器测试启动时系统广播打到接收器,65 条测试全灭、"Process crashed")。
 * 广播接收器必须"打不到就跳过",所以走 EntryPoint + runCatching。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun reminderPlanner(): ReminderPlanner
    fun reminderNotifier(): ReminderNotifier
}

/** 取依赖;组件不可用时返回 null,调用方直接放弃这一次广播 */
internal fun reminderEntryPoint(context: android.content.Context): ReminderEntryPoint? =
    runCatching {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReminderEntryPoint::class.java,
        )
    }.onFailure {
        // 取不到依赖就丢广播,必须留下原因 —— 否则"通知没响"只能靠猜
        android.util.Log.w("lnx-remind", "entry point unavailable", it)
    }.getOrNull()
