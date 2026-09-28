package com.lnx.app.core.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 本地时间 ⇄ epoch millis 的唯一转换点。
 * 用 Lazy getter 而非常量:时区是运行期属性,设备切换时区后必须生效(spec §4.6 已知限制的前提)。
 * 内部所有转换都必须走这里,避免同一换算出现两份实现而产生静默偏移。
 */
internal fun LocalDateTime.toEpochMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
