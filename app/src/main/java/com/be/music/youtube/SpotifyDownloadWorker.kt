package com.be.music.youtube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import com.be.music.R
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.be.music.ads.RewardedAdManager
import java.io.File

class SpotifyDownloadWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val channelId = "spotify_download_channel"
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val searcher = YoutubeSearcher()

    override suspend fun doWork(): Result {
        val playlistUrl = inputData.getString("playlist_url") ?: return Result.failure(
            workDataOf("error" to appContext.getString(R.string.playlist_url_not_found))
        )
        val quality = inputData.getString("quality") ?: "720p"
        val downloadLyrics = inputData.getBoolean("lyrics", false)

        createNotificationChannel()

        val notificationId = playlistUrl.hashCode()
        val notificationBuilder = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.spotify_fetching_playlist))
            .setContentText(appContext.getString(R.string.spotify_fetching_playlist))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, 0, true)

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notificationBuilder.build())
        }

        try {
            setForeground(foregroundInfo)
        } catch (_: Exception) {}

        // 1. Spotify playlist'ten şarkı listesini çek
        val tracks: List<SpotifyTrack>
        try {
            tracks = SpotifyPlaylistParser.parsePlaylist(playlistUrl)
        } catch (e: Exception) {
            val errorMsg = "${appContext.getString(R.string.spotify_download_error)}: ${e.message}"
            android.util.Log.e("SpotifyWorker", errorMsg, e)
            showErrorNotification(notificationId, errorMsg)
            return Result.failure(workDataOf("error" to errorMsg))
        }

        if (tracks.isEmpty()) {
            val errorMsg = appContext.getString(R.string.spotify_no_songs)
            showErrorNotification(notificationId, errorMsg)
            return Result.failure(workDataOf("error" to errorMsg))
        }

        val totalTracks = tracks.size
        var downloadedCount = 0
        var failedCount = 0

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (!downloadDir.exists()) downloadDir.mkdirs()

        // 2. Her şarkı için YouTube'da ara ve indir
        for ((index, track) in tracks.withIndex()) {
            val searchQuery = "${track.artist} ${track.name}".trim()
            val currentTrackLabel = "${track.name} - ${track.artist}"

            // Bildirim güncelle: arama aşaması
            notificationBuilder
                .setContentTitle(appContext.getString(R.string.spotify_fetching_playlist))
                .setContentText("$downloadedCount/$totalTracks | $currentTrackLabel")
                .setProgress(totalTracks, index, false)
            notificationManager.notify(notificationId, notificationBuilder.build())

            // Progress güncelle (UI için)
            setProgressAsync(workDataOf(
                "downloaded" to downloadedCount,
                "total" to totalTracks,
                "current_track" to currentTrackLabel,
                "progress" to 0
            ))

            try {
                // YouTube'da ara
                val searchResults = searcher.search(searchQuery)
                if (searchResults.isEmpty()) {
                    android.util.Log.w("SpotifyWorker", "Sonuç bulunamadı: $searchQuery")
                    failedCount++
                    continue
                }

                // En uyumlu ilk sonucu al
                val bestMatch = searchResults.first()
                val videoUrl = "https://www.youtube.com/watch?v=${bestMatch.id}"

                // İndirme isteği oluştur
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

                // İndir
                YoutubeDL.getInstance().execute(request) { progress, eta, _ ->
                    val trackProgress = progress.toInt().coerceIn(0, 100)
                    notificationBuilder
                        .setContentTitle(appContext.getString(R.string.spotify_fetching_playlist))
                        .setContentText("$downloadedCount/$totalTracks | $currentTrackLabel %$trackProgress")
                        .setProgress(100, trackProgress, false)
                    notificationManager.notify(notificationId, notificationBuilder.build())

                    setProgressAsync(workDataOf(
                        "downloaded" to downloadedCount,
                        "total" to totalTracks,
                        "current_track" to currentTrackLabel,
                        "progress" to trackProgress
                    ))
                }

                // MediaStore'a kaydet
                val files = downloadDir.listFiles()
                val currentTime = System.currentTimeMillis()
                files?.filter { currentTime - it.lastModified() < 30000 }?.forEach { file ->
                    android.media.MediaScannerConnection.scanFile(
                        appContext, arrayOf(file.absolutePath), null
                    ) { path, uri ->
                        android.util.Log.d("SpotifyWorker", "Scanned: $path -> $uri")
                    }
                }

                downloadedCount++
                RewardedAdManager(appContext).incrementDownloadCount()
            } catch (e: Exception) {
                android.util.Log.e("SpotifyWorker", "İndirme hatası: $searchQuery", e)
                failedCount++
            }
        }

        // 3. Tamamlandı bildirimi
        notificationManager.cancel(notificationId)
        val resultText = if (failedCount > 0) {
            "$downloadedCount/$totalTracks şarkı indirildi. $failedCount başarısız."
        } else {
            "$downloadedCount/$totalTracks şarkı başarıyla indirildi!"
        }
        val finalNotification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.spotify_playlist_done))
            .setContentText(resultText)
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

    private fun showErrorNotification(notificationId: Int, message: String) {
        notificationManager.cancel(notificationId)
        val errorNotification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.spotify_download_error))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()
        notificationManager.notify(notificationId + 2, errorNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                appContext.getString(R.string.spotify_playlist_done),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = appContext.getString(R.string.download_screen_title)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
