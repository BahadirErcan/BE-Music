package com.be.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey val name: String,
    val imageUrl: String? = null
)

@Serializable
@Entity(tableName = "albums")
data class Album(
    @PrimaryKey val name: String,
    val artistName: String,
    val releaseYear: String? = null,
    val coverPath: String? = null
)

@Serializable
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: Long,
    val title: String,
    val artistName: String,
    val albumName: String,
    val duration: Long,
    val path: String,
    val uriString: String,
    val dateModified: Long,
    val lyricsPath: String? = null,
    val isFavorite: Boolean = false
)
