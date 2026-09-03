package com.be.music.youtube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import com.be.music.R
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.be.music.data.AppDatabase
import com.be.music.premium.PremiumManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.be.music.ads.RewardedAdManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class BatchDownloadWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val channelId = "batch_download_channel"
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val searcher = YoutubeSearcher()
    private val downloadedPaths = mutableSetOf<String>()

    override suspend fun doWork(): Result {
        val trackListRaw = inputData.getString("track_list") ?: return Result.failure()
        val quality = inputData.getString("quality") ?: "720p"
        val downloadLyrics = inputData.getBoolean("lyrics", false)
        val parallelLimit = inputData.getInt("parallel_limit", 3)

        val trackLines = trackListRaw.split("\n").filter { it.isNotBlank() }
        if (trackLines.isEmpty()) return Result.failure()

        createNotificationChannel()

        val isPremium = PremiumManager.isPremiumStatic
        val notificationId = "batch_download".hashCode()
        val totalTracks = trackLines.size
        val createPlaylist = isPremium

        val playlistBaseName = readPlaylistBaseName()
        val numberPosition = readNumberPosition()
        val multiSong = readMultiSongPlaylist()

        if (isPremium) {
            return doParallelWork(trackLines, quality, notificationId, totalTracks, parallelLimit, createPlaylist, playlistBaseName, numberPosition, multiSong)
        } else {
            return doSequentialWork(trackLines, quality, notificationId, totalTracks, createPlaylist, playlistBaseName, numberPosition, multiSong)
        }
    }

    private fun readPlaylistBaseName(): String {
        return try {
            val db = AppDatabase.getDatabase(appContext)
            runBlocking { db.filterSettingsDao().getSettingsOnce()?.autoPlaylistName ?: "List" }
        } catch (e: Exception) {
            "List"
        }
    }

    private fun readNumberPosition(): String {
        return try {
            val db = AppDatabase.getDatabase(appContext)
            runBlocking { db.filterSettingsDao().getSettingsOnce()?.autoPlaylistNumberPosition ?: "SUFFIX" }
        } catch (e: Exception) {
            "SUFFIX"
        }
    }

    private fun readMultiSongPlaylist(): Boolean {
        return try {
            val db = AppDatabase.getDatabase(appContext)
            runBlocking { db.filterSettingsDao().getSettingsOnce()?.autoPlaylistMultiSong ?: true }
        } catch (e: Exception) {
            true
        }
    }

    private suspend fun doParallelWork(
        trackLines: List<String>,
        quality: String,
        notificationId: Int,
        totalTracks: Int,
        parallelLimit: Int,
        createPlaylist: Boolean,
        playlistBaseName: String,
        numberPosition: String,
        multiSong: Boolean
    ): Result {
        var downloadedCount = 0
        var failedCount = 0

        val notificationBuilder = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.download_list_title))
            .setContentText(appContext.getString(R.string.batch_preparing))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(totalTracks, 0, false)

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notificationBuilder.build())
        }
        try { setForeground(foregroundInfo) } catch (_: Exception) {}

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val initialFileNames = downloadDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        val semaphore = kotlinx.coroutines.sync.Semaphore(parallelLimit)

        coroutineScope {
            val jobs = trackLines.mapIndexed { index, trackQuery ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val currentLabel = trackQuery.trim()

                        withContext(Dispatchers.Main) {
                            notificationBuilder
                                .setContentTitle(appContext.getString(R.string.download_list_title))
                                .setContentText("$downloadedCount/$totalTracks | $currentLabel")
                                .setProgress(totalTracks, downloadedCount, false)
                            notificationManager.notify(notificationId, notificationBuilder.build())

                            setProgressAsync(workDataOf(
                                "downloaded" to downloadedCount,
                                "total" to totalTracks,
                                "current_track" to currentLabel,
                                "progress" to ((downloadedCount * 100) / totalTracks)
                            ))
                        }

                        try {
                            val searchResults = searcher.search(currentLabel)
                            if (searchResults.isEmpty()) {
                                android.util.Log.w("BatchWorker", "Sonuç bulunamadı: $currentLabel")
                                synchronized(this@BatchDownloadWorker) { failedCount++ }
                                return@withPermit
                            }

                            val bestMatch = searchResults.first()
                            val videoUrl = "https://www.youtube.com/watch?v=${bestMatch.id}"

                            val request = YoutubeDLRequest(videoUrl)
                            request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                            request.addOption("--extractor-args", "youtube:player-client=android,web")
                            request.addOption("--no-check-certificate")
                            request.addOption("--no-part")
                            request.addOption("--embed-metadata")
                            request.addOption("--embed-thumbnail")
                            request.addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
                            request.addOption("--extract-audio")
                            request.addOption("--audio-format", "m4a")

                            YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                                val trackProgress = progress.toInt().coerceIn(0, 100)
                                notificationBuilder
                                    .setContentTitle(appContext.getString(R.string.download_list_title))
                                    .setContentText("$currentLabel %$trackProgress")
                                    .setProgress(100, trackProgress, false)
                                notificationManager.notify(notificationId, notificationBuilder.build())

                                setProgressAsync(workDataOf(
                                    "downloaded" to downloadedCount,
                                    "total" to totalTracks,
                                    "current_track" to currentLabel,
                                    "progress" to trackProgress
                                ))
                            }

                            val files = downloadDir.listFiles()
                            files?.filter { it.name !in initialFileNames }?.forEach { file ->
                                downloadedPaths.add(file.absolutePath)
                                // Album metadata'sini duzelt
                                if (file.extension.lowercase() in listOf("mp3", "m4a", "aac", "mp4", "flac", "ogg", "opus")) {
                                    AlbumMetadataHelper.fixAlbumMetadata(file.absolutePath, playlistBaseName)
                                }
                                android.media.MediaScannerConnection.scanFile(
                                    appContext, arrayOf(file.absolutePath), null
                                ) { path, uri ->
                                    android.util.Log.d("BatchWorker", "Scanned: $path -> $uri")
                                }
                            }

                            synchronized(this@BatchDownloadWorker) {
                                downloadedCount++
                                RewardedAdManager(appContext).incrementDownloadCount()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BatchWorker", "İndirme hatası: $currentLabel", e)
                            synchronized(this@BatchDownloadWorker) { failedCount++ }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        return createPlaylistAndShowResult(notificationId, downloadedCount, totalTracks, failedCount, createPlaylist, playlistBaseName, numberPosition, multiSong)
    }

    private suspend fun doSequentialWork(
        trackLines: List<String>,
        quality: String,
        notificationId: Int,
        totalTracks: Int,
        createPlaylist: Boolean,
        playlistBaseName: String,
        numberPosition: String,
        multiSong: Boolean
    ): Result {
        var downloadedCount = 0
        var failedCount = 0

        val notificationBuilder = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.download_list_title))
            .setContentText(appContext.getString(R.string.batch_preparing))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(totalTracks, 0, false)

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notificationBuilder.build())
        }
        try { setForeground(foregroundInfo) } catch (_: Exception) {}

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val initialFileNames = downloadDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        for ((index, trackQuery) in trackLines.withIndex()) {
            val currentLabel = trackQuery.trim()

            notificationBuilder
                .setContentTitle(appContext.getString(R.string.download_list_title))
                .setContentText("$currentLabel - ${appContext.getString(R.string.search)}")
                .setProgress(totalTracks, index, false)
            notificationManager.notify(notificationId, notificationBuilder.build())

            setProgressAsync(workDataOf(
                "downloaded" to downloadedCount,
                "total" to totalTracks,
                "current_track" to currentLabel,
                "progress" to ((downloadedCount * 100) / totalTracks)
            ))

            try {
                val searchResults = searcher.search(currentLabel)
                if (searchResults.isEmpty()) {
                    android.util.Log.w("BatchWorker", "Sonuç bulunamadı: $currentLabel")
                    failedCount++
                    continue
                }

                val bestMatch = searchResults.first()
                val videoUrl = "https://www.youtube.com/watch?v=${bestMatch.id}"

                val request = YoutubeDLRequest(videoUrl)
                request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                request.addOption("--extractor-args", "youtube:player-client=android,web")
                request.addOption("--no-check-certificate")
                request.addOption("--no-part")
                request.addOption("--embed-metadata")
                request.addOption("--embed-thumbnail")
                request.addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
                request.addOption("--extract-audio")
                request.addOption("--audio-format", "m4a")

                YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                    val trackProgress = progress.toInt().coerceIn(0, 100)
                    notificationBuilder
                        .setContentTitle(appContext.getString(R.string.download_list_title))
                        .setContentText("$currentLabel %$trackProgress")
                        .setProgress(100, trackProgress, false)
                    notificationManager.notify(notificationId, notificationBuilder.build())

                    setProgressAsync(workDataOf(
                        "downloaded" to downloadedCount,
                        "total" to totalTracks,
                        "current_track" to currentLabel,
                        "progress" to trackProgress
                    ))
                }

                val files = downloadDir.listFiles()
                files?.filter { it.name !in initialFileNames }?.forEach { file ->
                    downloadedPaths.add(file.absolutePath)
                    // Album metadata'sini duzelt
                    if (file.extension.lowercase() in listOf("mp3", "m4a", "aac", "mp4", "flac", "ogg", "opus")) {
                        AlbumMetadataHelper.fixAlbumMetadata(file.absolutePath, playlistBaseName)
                    }
                    android.media.MediaScannerConnection.scanFile(
                        appContext, arrayOf(file.absolutePath), null
                    ) { path, uri ->
                        android.util.Log.d("BatchWorker", "Scanned: $path -> $uri")
                    }
                }

                downloadedCount++
                RewardedAdManager(appContext).incrementDownloadCount()
            } catch (e: Exception) {
                android.util.Log.e("BatchWorker", "İndirme hatası: $currentLabel", e)
                failedCount++
            }
        }

        return createPlaylistAndShowResult(notificationId, downloadedCount, totalTracks, failedCount, createPlaylist, playlistBaseName, numberPosition, multiSong)
    }

    private suspend fun createPlaylistAndShowResult(
        notificationId: Int,
        downloadedCount: Int,
        totalTracks: Int,
        failedCount: Int,
        createPlaylist: Boolean,
        playlistBaseName: String,
        numberPosition: String,
        multiSong: Boolean
    ): Result {
        // Create playlist from downloaded songs
        var playlistResult: Pair<String, Int>? = null
        if (createPlaylist && downloadedPaths.isNotEmpty()) {
            playlistResult = createPlaylistFromDownloaded(
                downloadedPaths.toList(),
                playlistBaseName,
                numberPosition,
                multiSong
            )
        }

        notificationManager.cancel(notificationId)
        val resultText = if (failedCount > 0) {
            "$downloadedCount/$totalTracks indirildi. $failedCount başarısız."
        } else {
            "$downloadedCount/$totalTracks başarıyla indirildi!"
        }
        
        var contentText = resultText
        if (playlistResult != null) {
            contentText = appContext.getString(R.string.batch_playlist_created, playlistResult.first, playlistResult.second)
        }
        
        val finalNotification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.list_download_complete))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(notificationId + 1, finalNotification)

        return Result.success(workDataOf(
            "downloaded" to downloadedCount,
            "total" to totalTracks,
            "failed" to failedCount
        ))
    }

    private suspend fun createPlaylistFromDownloaded(
        paths: List<String>,
        baseName: String,
        numberPosition: String,
        multiSong: Boolean
    ): Pair<String, Int>? {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val uris = scanFilesAndGetUris(paths)
                if (uris.isEmpty()) {
                    android.util.Log.w("BatchWorker", "No MediaStore URIs for downloaded files")
                    return@withContext null
                }

                val songs = buildSongsFromUris(uris)
                if (songs.isEmpty()) {
                    android.util.Log.w("BatchWorker", "No songs built from downloaded URIs")
                    return@withContext null
                }

                db.songDao().insertSongs(songs)

                val dao = db.playlistDao()
                val createdPlaylists = mutableListOf<Pair<String, Int>>()

                if (multiSong) {
                    val playlistName = findNextPlaylistName(dao, baseName, numberPosition)
                    val songIds = songs.map { it.id }
                    val playlist = com.be.music.data.Playlist(name = playlistName, songIds = songIds)
                    dao.insertPlaylist(playlist)
                    android.util.Log.d("BatchWorker", "Created playlist: $playlistName with ${songIds.size} songs")
                    createdPlaylists.add(Pair(playlistName, songIds.size))
                } else {
                    songs.forEach { song ->
                        val playlistName = findNextPlaylistName(dao, baseName, numberPosition)
                        val playlist = com.be.music.data.Playlist(
                            name = playlistName,
                            songIds = listOf(song.id)
                        )
                        dao.insertPlaylist(playlist)
                        android.util.Log.d("BatchWorker", "Created single-song playlist: $playlistName for song ${song.id}")
                        createdPlaylists.add(Pair(playlistName, 1))
                    }
                }

                createdPlaylists.lastOrNull()
            } catch (e: Exception) {
                android.util.Log.e("BatchWorker", "Error creating playlist", e)
                null
            }
        }
    }

    private suspend fun scanFilesAndGetUris(paths: List<String>): List<Uri> = suspendCoroutine { cont ->
        if (paths.isEmpty()) {
            cont.resume(emptyList())
            return@suspendCoroutine
        }
        val result = java.util.Collections.synchronizedList(mutableListOf<Uri>())
        val pending = AtomicInteger(paths.size)
        try {
            android.media.MediaScannerConnection.scanFile(
                appContext,
                paths.toTypedArray(),
                null
            ) { _, uri ->
                uri?.let { result.add(it) }
                if (pending.decrementAndGet() == 0) {
                    cont.resume(result.toList())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BatchWorker", "scanFile failed", e)
            cont.resume(result.toList())
        }
    }

    private fun buildSongsFromUris(uris: List<Uri>): List<com.be.music.data.Song> {
        val songs = mutableListOf<com.be.music.data.Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        for (uri in uris) {
            try {
                appContext.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        val title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                        val artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
                        val album = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
                        val duration = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                        val path = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
                        val dateModified = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED))
                        val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val lyricsPath = findLyricsPathForSong(path)
                        songs.add(
                            com.be.music.data.Song(
                                id = id,
                                title = title,
                                artistName = artist,
                                albumName = album,
                                duration = duration,
                                path = path,
                                uriString = songUri.toString(),
                                dateModified = dateModified,
                                lyricsPath = lyricsPath
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BatchWorker", "Error reading song metadata: $uri", e)
            }
        }
        return songs
    }

    private fun findLyricsPathForSong(path: String): String? {
        val basePath = path.substringBeforeLast('.')
        val lyricFiles = listOf(
            File("$basePath.vtt"),
            File("$basePath.srt"),
            File("$basePath.lrc")
        )
        for (file in lyricFiles) {
            if (file.exists()) return file.absolutePath
        }
        return null
    }

    private suspend fun findNextPlaylistName(
        playlistDao: com.be.music.data.PlaylistDao,
        baseName: String,
        numberPosition: String
    ): String {
        val safeBase = baseName.ifBlank { "List" }
        val existingNames = playlistDao.getAllPlaylistNames()
        val numbers = existingNames.mapNotNull { name ->
            when (numberPosition) {
                "PREFIX" -> {
                    val parts = name.split("-", limit = 2)
                    if (parts.size == 2 && parts[1] == safeBase) parts[0].toIntOrNull() else null
                }
                else -> {
                    val parts = name.split("-", limit = 2)
                    if (parts.size == 2 && parts[0] == safeBase) parts[1].toIntOrNull() else null
                }
            }
        }
        val nextNumber = if (numbers.isEmpty()) 1 else numbers.max()!! + 1
        return when (numberPosition) {
            "PREFIX" -> "$nextNumber-$safeBase"
            else -> "$safeBase-$nextNumber"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                appContext.getString(R.string.download_list_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = appContext.getString(R.string.download_screen_title)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
