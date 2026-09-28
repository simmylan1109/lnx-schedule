package com.lnx.app.core.domain.model

/**
 * 标签(spec §3.10):全部用户创建;颜色是色位编号,翻译随主题。
 * colorSlot 与事件色板共用 8 色位。
 */
data class Tag(
    val id: String,
    val name: String,
    val colorSlot: Int,
)
