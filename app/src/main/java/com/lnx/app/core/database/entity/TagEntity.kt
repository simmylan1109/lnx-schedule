package com.lnx.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 标签表(spec §3.10):全部由用户创建,不预置;删除 = 软删除(仅解除关联,不动事件) */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 0..7 色位(spec §5.4),翻译随主题 */
    val colorSlot: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

/** 事件 ↔ 标签 多对多关联(spec §3.10);删除标签时关联一并删除 */
@Entity(tableName = "event_tag_cross_ref", primaryKeys = ["eventId", "tagId"])
data class EventTagCrossRef(
    val eventId: String,
    val tagId: String,
)
