package com.be.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
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
    private val filterSettingsDao: FilterSettingsDao,
    private val songOverrideDao: SongOverrideDao,
    val playHistoryManager: PlayHistoryManager
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

        // Kullanıcının düzenlediği şarkıların değerleri taramayı ezmeyecek şekilde
        // MediaStore satırlarına uygulanır (MediaStore güncellenemese bile düzenleme kalıcı kalır).
        val overrides = songOverrideDao.getAllOnce().associateBy { it.songId }

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

                    val override = overrides[id]
                    val effectiveTitle = override?.title ?: title
                    val effectiveArtist = override?.artist ?: artist
                    val effectiveAlbum = override?.album ?: album
                    val effectivePath = override?.path ?: path
                    val effectiveDateModified = if (override != null && override.dateModified > 0L) override.dateModified else dateModified

                    songsList.add(
                        Song(
                            id = id,
                            title = effectiveTitle,
                            artistName = effectiveArtist,
                            albumName = effectiveAlbum,
                            duration = duration,
                            path = effectivePath,
                            uriString = songUri.toString(),
                            dateModified = effectiveDateModified,
                            lyricsPath = findLyricsPathForSong(effectivePath)
                        )
                    )
                    artistsSet.add(effectiveArtist)
                    albumsMap[effectiveAlbum] = effectiveArtist
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

    private val playlistDir: File by lazy {
        File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "Playlist")
    }

    private fun ensurePlaylistDir(): File {
        if (!playlistDir.exists()) playlistDir.mkdirs()
        return playlistDir
    }

    private suspend fun exportPlaylistToJson(playlist: Playlist): String? = withContext(Dispatchers.IO) {
        try {
            val plDir = ensurePlaylistDir()

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
            val newDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "Playlist")
            val oldDirs = listOfNotNull(
                context.getExternalFilesDir(null)?.let { File(it, "Music/pl") },
                File(context.filesDir, "Music/pl")
            )
            val dirsToCheck = listOf(newDir) + oldDirs

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

                val plDir = ensurePlaylistDir()

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
        val oldFile = File(song.path)
        var currentPath = song.path
        var currentLyricsPath = song.lyricsPath

        // Düzenleme tarihi sıralamasını bozmasın diye orijinal mtime korunur.
        // (song.dateModified = MediaStore DATE_MODIFIED, saniye cinsinden)
        val originalMtimeMillis = if (song.dateModified > 0L) song.dateModified * 1000L else oldFile.lastModified()

        // 0. Dosyanın gerçek metadata'sını kalıcı olarak güncelle (jaudiotagger).
        // Rename'den ÖNCE yapılır ki dosya her zaman erişilebilir olsun ve etiketler beraber taşınsın.
        // commit() dosyayı yeniden yazdığı için mtime'ı değiştirir; işlem sonrası eski tarih geri yüklenir.
        try {
            writeAudioMetadata(oldFile, newTitle, newArtist, newAlbum)
            if (originalMtimeMillis > 0L) oldFile.setLastModified(originalMtimeMillis)
            Log.d("MusicRepository", "Dosya metadata'si kalici olarak guncellendi: ${oldFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("MusicRepository", "Dosya metadata yazma hatasi", e)
        }

        val parentDir = oldFile.parentFile
        val extension = oldFile.extension
        val sanitizedTitle = newTitle.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val newFileName = "$sanitizedTitle.$extension"
        val newFile = parentDir?.let { File(it, newFileName) }
        val shouldRename = newFile != null && newFile.absolutePath != oldFile.absolutePath && !newFile.exists()

        // 1. MediaStore güncelle.
        // API 29+: DATA sütunu update ile yazılamaz (provider tüm güncellemeyi reddeder).
        // Bu yüzden DATA yerine DISPLAY_NAME + RELATIVE_PATH kullanılır; provider dosyayı
        // kendisi taşır, aynı _ID korunur ve DATA sütunu da güncellenir.
        // API 28 ve öncesi: mevcut DATA yaklaşımı çalışır.
        val values = android.content.ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, newTitle)
            put(MediaStore.Audio.Media.ARTIST, newArtist)
            put(MediaStore.Audio.Media.ALBUM, newAlbum)
            if (shouldRename) {
                val nf = newFile!!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = try {
                        val root = File(Environment.getExternalStorageDirectory().absolutePath)
                        val rel = root.toURI().relativize(parentDir!!.toURI()).path
                        if (rel.startsWith("..") || rel.contains("../")) null else rel
                    } catch (e: Exception) {
                        null
                    }
                    if (relativePath != null) {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    }
                } else {
                    put(MediaStore.Audio.Media.DATA, nf.absolutePath)
                }
            }
        }
        val songUri = android.content.ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id
        )
        var mediaStoreUpdated = false
        var renamedByProvider = false
        try {
            val rows = context.contentResolver.update(songUri, values, null, null)
            mediaStoreUpdated = rows > 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && shouldRename && mediaStoreUpdated) {
                val nf = newFile!!
                renamedByProvider = true
                currentPath = nf.absolutePath
                // Provider dosyayı taşıdı; taşınan dosyanın eski tarihini de koru.
                if (nf.exists() && originalMtimeMillis > 0L) nf.setLastModified(originalMtimeMillis)
            }
            Log.d("MusicRepository", "MediaStore guncellendi: $rows satir")
        } catch (e: Exception) {
            Log.e("MusicRepository", "MediaStore guncelleme hatasi", e)
        }

        // Ana güncellemeden bağımsız: MediaStore'daki DATE_MODIFIED'i eski değerinde tut.
        if (song.dateModified > 0L) {
            try {
                val dateValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_MODIFIED, song.dateModified)
                }
                context.contentResolver.update(songUri, dateValues, null, null)
            } catch (e: Exception) {
                Log.e("MusicRepository", "DATE_MODIFIED geri yuklenemedi", e)
            }
        }

        // 2. Fiziksel dosya adını güncelle (yalnızca MediaStore taşıyamadıysa veya eski Android).
        // Başarısız olursa diğer adımları engellemez.
        if (shouldRename && !renamedByProvider && oldFile.exists()) {
            val physicalNewFile = newFile!!
            if (oldFile.renameTo(physicalNewFile)) {
                currentPath = physicalNewFile.absolutePath
                // Manuel taşımada da dosyanın eski tarihini koru (yeni eklenmiş görünmesin).
                if (physicalNewFile.exists() && originalMtimeMillis > 0L) {
                    physicalNewFile.setLastModified(originalMtimeMillis)
                }

                // Rename associated lyrics file if exists
                val renameDir = physicalNewFile.parentFile
                song.lyricsPath?.let { oldLyricsPathStr ->
                    val oldLyricsFile = File(oldLyricsPathStr)
                    if (oldLyricsFile.exists()) {
                        val lyricsExt = oldLyricsFile.extension
                        val newLyricsFile = File(renameDir, "$sanitizedTitle.$lyricsExt")
                        if (oldLyricsFile.renameTo(newLyricsFile)) {
                            currentLyricsPath = newLyricsFile.absolutePath
                        }
                    }
                }
            }
        }

        // 3. Room DB güncelle (arayüz buradan beslenir, HER ZAMAN çalışmalı)
        try {
            val updatedSong = song.copy(
                title = newTitle,
                artistName = newArtist,
                albumName = newAlbum,
                path = currentPath,
                lyricsPath = currentLyricsPath
            )
            songDao.updateSong(updatedSong)
            // Düzenlemeyi kalıcı yap: sonraki tarama (forceRefresh dahil) MediaStore'u
            // baz alsa bile bu değerler korunur.
            songOverrideDao.upsert(
                SongOverride(
                    songId = song.id,
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    path = currentPath,
                    dateModified = originalMtimeMillis / 1000L
                )
            )
            Log.d("MusicRepository", "Room DB guncellendi: ${updatedSong.title}")
        } catch (e: Exception) {
            Log.e("MusicRepository", "Room DB guncelleme hatasi", e)
        }
    }

    private fun writeAudioMetadata(file: File, title: String, artist: String, album: String) {
        if (!file.exists()) return
        val extension = file.extension.lowercase()
        if (extension !in listOf("mp3", "m4a", "aac", "mp4", "flac", "ogg", "opus")) return

        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
        val tag = audioFile.tag ?: audioFile.createDefaultTag() ?: return
        tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
        tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
        tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, album)
        audioFile.commit()
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

            // 5. Kalıcı düzenleme override'ını da temizle
            songOverrideDao.deleteBySongId(song.id)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Şarkı silinemedi", e)
        }
    }
}

