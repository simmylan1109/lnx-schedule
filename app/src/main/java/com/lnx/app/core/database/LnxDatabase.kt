package com.lnx.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.entity.EventEntity

@Database(entities = [EventEntity::class], version = 1, exportSchema = true)
abstract class LnxDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
