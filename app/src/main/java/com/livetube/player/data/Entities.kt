package com.livetube.player.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_items")
data class LibraryItemEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val url: String,
    val name: String,
    val thumbnail: String?,
    val pageBlob: String?,
    val addedAt: Long,
)

@Entity(tableName = "cached_streams", primaryKeys = ["itemId", "position"])
data class CachedStreamEntity(
    val itemId: String,
    val position: Int,
    val title: String,
    val videoUrl: String,
    val thumbnail: String?,
    val durationSec: Long,
    val isLive: Boolean,
)