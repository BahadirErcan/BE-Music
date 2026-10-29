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
    // songId -> çalma listesine eklenme zaman damgası (epoch ms).
    // Tarihe göre sıralamada indirme tarihi değil, bu değer baz alınır.
    val songAddedAt: Map<Long, Long> = emptyMap(),
    val filePath: String? = null,
    val sortMode: String = "ADDED", // ALPHA, DURATION, ADDED (çalma listesine özel)
    val sortReverse: Boolean = false
)
