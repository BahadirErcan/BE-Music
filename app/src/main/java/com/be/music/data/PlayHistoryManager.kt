package com.be.music.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class HistoryEntry(
    val songId: Long,
    val songTitle: String,
    val artistName: String,
    val albumName: String,
    val duration: Long,
    val path: String,
    val uriString: String,
    val playedAt: Long
)

@Serializable
data class HistoryDay(
    val date: String,
    val entries: List<HistoryEntry>
)

@Singleton
class PlayHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file = File(context.filesDir, "play_history.json")
    private val _history = MutableStateFlow<List<HistoryDay>>(emptyList())
    val history: Flow<List<HistoryDay>> = _history.asStateFlow()

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    init {
        load()
    }

    private fun load() {
        try {
            if (file.exists()) {
                val json = file.readText()
                if (json.isNotBlank()) {
                    _history.value = Json.decodeFromString<List<HistoryDay>>(json)
                }
            }
        } catch (e: Exception) {
            _history.value = emptyList()
        }
    }

    private fun save() {
        try {
            file.writeText(Json.encodeToString(_history.value))
        } catch (_: Exception) {}
    }

    suspend fun addEntry(song: Song) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cooldownMs = 30_000L

        // Ayni sarki 30 saniye icinde tekrar calindiysa atla
        val today = dateFormat.format(Date())
        val current = _history.value.toMutableList()
        val todayIndex = current.indexOfFirst { it.date == today }
        if (todayIndex >= 0) {
            val todayEntries = current[todayIndex].entries
            val lastPlay = todayEntries.firstOrNull { it.songId == song.id }
            if (lastPlay != null && (now - lastPlay.playedAt) < cooldownMs) {
                return@withContext
            }
        }

        val entry = HistoryEntry(
            songId = song.id,
            songTitle = song.title,
            artistName = song.artistName,
            albumName = song.albumName,
            duration = song.duration,
            path = song.path,
            uriString = song.uriString,
            playedAt = now
        )
        if (todayIndex >= 0) {
            val day = current[todayIndex]
            current[todayIndex] = day.copy(entries = listOf(entry) + day.entries)
        } else {
            current.add(0, HistoryDay(date = today, entries = listOf(entry)))
        }
        _history.value = current
        save()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        _history.value = emptyList()
        save()
    }
}
