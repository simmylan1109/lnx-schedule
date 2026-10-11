package com.lnx.app.core.di

import javax.inject.Qualifier

/**
 * 两个 DataStore(设置 / 同步状态)的类型都是 `DataStore<Preferences>`,
 * Hilt 靠限定符区分是哪一个——缺了限定符 Dagger 直接报 "bound multiple times"
 * (v0.3 A6 整改:给同步状态补模块注入时踩过,主源集与 androidTest 双双编译失败)。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncStore
