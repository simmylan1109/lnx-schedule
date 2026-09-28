package com.lnx.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.TagDao
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventTagCrossRef
import com.lnx.app.core.database.entity.TagEntity

@Database(
    entities = [EventEntity::class, TagEntity::class, EventTagCrossRef::class],
    version = 2,
    exportSchema = true,
)
abstract class LnxDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    abstract fun tagDao(): TagDao
}
