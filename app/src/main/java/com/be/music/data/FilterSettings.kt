package com.be.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "filter_settings")
data class FilterSettings(
    @PrimaryKey val id: Int = 1,
    val filterMode: String = "NONE", // NONE, BLACKLIST, WHITELIST
    val durationThresholdSec: Int = 50, // Default 50s
    val folders: List<String> = emptyList(), // folder paths
    val themeMode: String = "SYSTEM", // LIGHT, DARK, SYSTEM
    val language: String = "SYSTEM", // SYSTEM, en, tr, de, pt, ar, bn, es, fr, zh, ru
    val themeColor: String = "ACCENT", // ACCENT, GREEN, BLUE, RED, PURPLE, YELLOW
    val downloadLyricsEnabled: Boolean = false,
    val preferredVideoQuality: String = "720p",
    val sleepTimerMinutes: Int = 0,
    val parallelDownloadCount: Int = 3,
    val autoPlaylistName: String = "List",
    val autoPlaylistNumberPosition: String = "SUFFIX", // PREFIX, SUFFIX
    val autoPlaylistMultiSong: Boolean = true, // true: çoklu şarkıyı tek playlist'te topla
    val skipSilenceEnabled: Boolean = false, // true: şarkılardaki sessiz yerleri otomatik atla
    val songSortOrder: String = "DATE", // NAME, DATE, DURATION (ana ekran şarkı sıralaması)
    val songSortReverse: Boolean = false
)
