package com.be.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val songIds: List<Long>,
    val filePath: String? = null,
    val sortMode: String = "ADDED", // ALPHA, DURATION, ADDED (çalma listesine özel)
    val sortReverse: Boolean = false
)
