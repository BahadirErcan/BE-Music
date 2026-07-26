package com.be.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton


class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val playlistDao: PlaylistDao,
    private val filterSettingsDao: FilterSettingsDao
) {
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val allArtists: Flow<List<Artist>> = artistDao.getAllArtists()
    val allAlbums: Flow<List<Album>> = albumDao.getAllAlbums()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    val filterSettings: Flow<FilterSettings> = filterSettingsDao.getSettings()
        .map { it ?: FilterSettings() }

    val filteredSongs: Flow<List<Song>> = songDao.getAllSongs()
        .combine(filterSettings) { songs, settings ->
            songs.filter { song ->
                val durationSec = song.duration / 1000
                if (durationSec < settings.durationThresholdSec) {
                    return@filter false
                }

                when (settings.filterMode) {
                    "BLACKLIST" -> {
                        settings.folders.none { folder -> song.path.startsWith(folder) }
                    }
                    "WHITELIST" -> {
                        settings.folders.any { folder -> song.path.startsWith(folder) }
                    }
                    else -> true
                }
            }
        }

    suspend fun saveFilterSettings(settings: FilterSettings) {
        filterSettingsDao.saveSettings(settings)
    }

    suspend fun getSongsByIds(ids: List<Long>): List<Song> {
        return songDao.getSongsByIds(ids)
    }

    suspend fun scanLocalMusic(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        val existingSongs = songDao.getAllSongsOnce()
        if (!forceRefresh && existingSongs.isNotEmpty()) {
            Log.d("MusicRepository", "Skipping scan, songs already exist in DB")
            return@withContext
        }

        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val songsList = mutableListOf<Song>()
        val artistsSet = mutableSetOf<String>()
        val albumsMap = mutableMapOf<String, String>() // AlbumName to ArtistName

        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Unknown Song"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val album = c.getString(albumCol) ?: "Unknown Album"
                    val duration = c.getLong(durationCol)
                    val path = c.getString(dataCol) ?: ""
                    val dateModified = c.getLong(dateCol)
                    val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    songsList.add(
                        Song(
                            id = id,
                            title = title,
                            artistName = artist,
                            albumName = album,
                            duration = duration,
                            path = path,
                            uriString = songUri.toString(),
                            dateModified = dateModified,
                            lyricsPath = findLyricsPathForSong(path)
                        )
                    )
                    artistsSet.add(artist)
                    albumsMap[album] = artist
                }
            }

            if (songsList.isNotEmpty()) {
                songDao.deleteAllSongs()
                artistDao.deleteAll()
                albumDao.deleteAll()

                artistDao.insertArtists(artistsSet.map { Artist(it) })
                albumDao.insertAlbums(albumsMap.map { Album(it.key, it.value) })
                songDao.insertSongs(songsList)
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error scanning music", e)
        }
    }

    private fun findLyricsPathForSong(path: String): String? {
        val basePath = path.substringBeforeLast('.')
        val lyricFiles = listOf(
            File("$basePath.vtt"),
            File("$basePath.srt"),
            File("$basePath.lrc")
        )
        for (file in lyricFiles) {
            if (file.exists()) {
                return file.absolutePath
            }
        }
        return null
    }

    suspend fun updateSongLyricsPath(song: Song, lyricsPath: String?) = withContext(Dispatchers.IO) {
        songDao.updateSong(song.copy(lyricsPath = lyricsPath))
    }

    suspend fun createPlaylist(name: String, songIds: List<Long>): Playlist {
        val playlist = Playlist(name = name, songIds = songIds)
        val id = playlistDao.insertPlaylist(playlist)
        val finalPlaylist = playlist.copy(id = id.toInt())
        // attempt export but ignore path here
        try { exportPlaylistToJson(finalPlaylist) } catch (_: Exception) {}
        return finalPlaylist
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.updatePlaylist(playlist)
        try { exportPlaylistToJson(playlist) } catch (_: Exception) {}
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist)
        playlist.filePath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error deleting playlist file", e)
            }
        }
    }

    private suspend fun exportPlaylistToJson(playlist: Playlist): String? = withContext(Dispatchers.IO) {
        try {
            val externalBase = context.getExternalFilesDir(null)
            val baseDir = externalBase ?: context.filesDir
            val plDir = File(baseDir, "Music/pl")
            if (!plDir.exists()) plDir.mkdirs()

            val sanitizedName = playlist.name.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
            val plFile = File(plDir, "playlist_${playlist.id}_$sanitizedName.json")

            val jsonString = Json.encodeToString(playlist)
            FileOutputStream(plFile).use { fos ->
                fos.write(jsonString.toByteArray())
            }

            val updatedPlaylist = playlist.copy(filePath = plFile.absolutePath)
            playlistDao.updatePlaylist(updatedPlaylist)
            plFile.absolutePath
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error exporting playlist to JSON", e)
            null
        }
    }

    // Public helper to export a playlist and get the exported file path (or null on failure)
    suspend fun exportPlaylistFilePath(playlist: Playlist): String? = exportPlaylistToJson(playlist)

    suspend fun importPlaylistsFromJson() = withContext(Dispatchers.IO) {
        try {
            val externalBase = context.getExternalFilesDir(null)
            val dirsToCheck = listOfNotNull(
                externalBase?.let { File(it, "Music/pl") },
                File(context.filesDir, "Music/pl")
            )

            for (plDir in dirsToCheck) {
                if (plDir.exists() && plDir.isDirectory) {
                    val files = plDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
                    for (file in files) {
                        try {
                            val jsonString = file.readText()
                            val importedPlaylist = Json.decodeFromString<Playlist>(jsonString)
                            val existing = playlistDao.getPlaylistByName(importedPlaylist.name)
                            if (existing == null) {
                                playlistDao.insertPlaylist(
                                    Playlist(
                                        name = importedPlaylist.name,
                                        songIds = importedPlaylist.songIds,
                                        filePath = file.absolutePath
                                    )
                                )
                            } else {
                                playlistDao.updatePlaylist(
                                    existing.copy(
                                        songIds = importedPlaylist.songIds,
                                        filePath = file.absolutePath
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("MusicRepository", "Failed to import file ${file.name}", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error during importing playlists", e)
        }
    }

    suspend fun importPlaylistFromUri(uri: Uri): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val importedPlaylist = Json.decodeFromString<Playlist>(jsonString)

                val externalBase = context.getExternalFilesDir(null) ?: context.filesDir
                val plDir = File(externalBase, "Music/pl")
                if (!plDir.exists()) plDir.mkdirs()

                val sanitizedName = importedPlaylist.name.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
                val plFile = File(plDir, "playlist_${System.currentTimeMillis()}_$sanitizedName.json")
                FileOutputStream(plFile).use { fos ->
                    fos.write(jsonString.toByteArray())
                }

                val existing = playlistDao.getPlaylistByName(importedPlaylist.name)
                if (existing == null) {
                    playlistDao.insertPlaylist(
                        Playlist(
                            name = importedPlaylist.name,
                            songIds = importedPlaylist.songIds,
                            filePath = plFile.absolutePath
                        )
                    )
                } else {
                    playlistDao.updatePlaylist(
                        existing.copy(
                            songIds = importedPlaylist.songIds,
                            filePath = plFile.absolutePath
                        )
                    )
                }
                Pair(true, "Playlist imported successfully")
            } ?: Pair(false, "Unable to open selected file")
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error importing playlist from URI", e)
            Pair(false, "Playlist import failed: ${e.message ?: "Unknown error"}")
        }
    }

    suspend fun updateSongMetadata(song: Song, newTitle: String, newArtist: String, newAlbum: String) = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(song.path)
            var currentPath = song.path
            var currentLyricsPath = song.lyricsPath

            // Physical rename if old file exists and new name differs
            val parentDir = oldFile.parentFile
            if (parentDir != null && oldFile.exists()) {
                val extension = oldFile.extension
                val sanitizedTitle = newTitle.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                val newFile = File(parentDir, "$sanitizedTitle.$extension")
                if (newFile.absolutePath != oldFile.absolutePath && !newFile.exists()) {
                    if (oldFile.renameTo(newFile)) {
                        currentPath = newFile.absolutePath

                        // Rename associated lyrics file if exists
                        song.lyricsPath?.let { oldLyricsPathStr ->
                            val oldLyricsFile = File(oldLyricsPathStr)
                            if (oldLyricsFile.exists()) {
                                val lyricsExt = oldLyricsFile.extension
                                val newLyricsFile = File(parentDir, "$sanitizedTitle.$lyricsExt")
                                if (oldLyricsFile.renameTo(newLyricsFile)) {
                                    currentLyricsPath = newLyricsFile.absolutePath
                                }
                            }
                        }
                    }
                }
            }

            // 1. MediaStore'u Güncelle
            val values = android.content.ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, newTitle)
                put(MediaStore.Audio.Media.ARTIST, newArtist)
                put(MediaStore.Audio.Media.ALBUM, newAlbum)
                if (currentPath != song.path) {
                    put(MediaStore.Audio.Media.DATA, currentPath)
                }
            }
            val songUri = android.content.ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id
            )
            context.contentResolver.update(songUri, values, null, null)

            // 2. Dosyayı yeniden taratarak güncel bilgileri sisteme kaydet
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(currentPath),
                null
            ) { path, uri ->
                Log.d("MusicRepository", "Tarandı ve güncellendi: $path -> $uri")
            }

            // 3. Room DB'yi Güncelle
            val updatedSong = song.copy(
                title = newTitle,
                artistName = newArtist,
                albumName = newAlbum,
                path = currentPath,
                lyricsPath = currentLyricsPath
            )
            songDao.updateSong(updatedSong)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Metadata güncellenemedi", e)
        }
    }

    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        try {
            // 1. Fiziksel dosyayı diskten sil (Madde 7)
            val file = File(song.path)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d("MusicRepository", "Fiziksel dosya silindi: $deleted, Yol: ${song.path}")
            }

            // 2. Lyrics dosyasını sil
            song.lyricsPath?.let { lyricsPathStr ->
                val lyricsFile = File(lyricsPathStr)
                if (lyricsFile.exists()) {
                    val deleted = lyricsFile.delete()
                    Log.d("MusicRepository", "Fiziksel lyrics dosyası silindi: $deleted, Yol: $lyricsPathStr")
                }
            }

            // 3. MediaStore'dan sil
            val songUri = android.content.ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id
            )
            try {
                context.contentResolver.delete(songUri, null, null)
            } catch (securityException: SecurityException) {
                Log.e("MusicRepository", "MediaStore silme hatası ama devam ediliyor", securityException)
            }

            // 4. Room DB'den sil
            songDao.deleteSongs(listOf(song))
        } catch (e: Exception) {
            Log.e("MusicRepository", "Şarkı silinemedi", e)
        }
    }
}

