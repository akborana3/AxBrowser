package com.akay.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String?,
    @ColumnInfo(name = "visit_count") val visitCount: Int = 1,
    @ColumnInfo(name = "last_visited") val lastVisited: Long
)
