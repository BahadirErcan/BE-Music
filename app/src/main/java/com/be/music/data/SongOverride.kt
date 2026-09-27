package com.be.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Kullanıcının şarkı bilgisi düzenlemelerini kalıcı tutar.
// Tarama (scanLocalMusic) MediaStore'dan listeyi yeniden kurarken bu değerleri
// uygular; böylece MediaStore satırı güncellenemese bile düzenleme geri dönmez.
@Entity(tableName = "song_overrides")
data class SongOverride(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val path: String? = null,
    val dateModified: Long = 0L
)