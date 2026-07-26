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
import com.be.music.premium.PremiumManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

class DownloadWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val channelId = "download_channel"
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val videoId = inputData.getString("video_id") ?: return Result.failure()
        val videoTitle = inputData.getString("video_title") ?: ""
        val type = inputData.getString("type") ?: "m4a"
        val quality = inputData.getString("quality") ?: "720p"
        val downloadLyrics = inputData.getBoolean("lyrics", false)
        val videoUrl = "https://www.youtube.com/watch?v=$videoId"

        createNotificationChannel()

        val notificationId = videoId.hashCode()
        val downloadTypeLabel = if (type == "m4a") appContext.getString(R.string.type_song) else appContext.getString(R.string.type_video)
        val downloadTypeCode = if (type == "m4a") "S" else "V"
        val notificationBuilder = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.downloaded_format, downloadTypeCode, videoTitle))
            .setContentText("Hazırlanıyor...")
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

        val downloadDir = if (type == "m4a") {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        } else {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            File(moviesDir, "BE-Music")
        }

        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val request = YoutubeDLRequest(videoUrl)
        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
        request.addOption("--extractor-args", "youtube:player-client=android,web")
        request.addOption("--no-check-certificate")
        request.addOption("--no-part")
        request.addOption("--embed-metadata")
        request.addOption("--embed-thumbnail")
        request.addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")

        if (type == "m4a") {
            request.addOption("--extract-audio")
            request.addOption("--audio-format", "m4a")
        } else {
            val format = DownloadFormatResolver.resolveVideoFormat(type, quality)
            request.addOption("-f", format)
            request.addOption("--merge-output-format", "mp4")
        }

        val isPremium = PremiumManager.isPremiumStatic

        if (isPremium && !downloadLyrics) {
            // Premium + lyrics kapalı → altyazı kontrolünü tamamen atla, direkt indir
            return executeDownload(request, notificationBuilder, notificationId, downloadTypeLabel, downloadTypeCode, downloadDir, null, channelId, videoTitle)
        }

        if (isPremium && downloadLyrics) {
            // Premium + lyrics açık → indirmeyi başlat, altyazıyı paralel çek
            return executePremiumParallel(videoUrl, request, downloadDir, notificationBuilder, notificationId, downloadTypeLabel, downloadTypeCode, channelId, videoTitle)
        }

        // Normal kullanıcı → eski akış (sıralı)
        return executeNormalFlow(videoUrl, request, downloadDir, notificationBuilder, notificationId, downloadTypeLabel, downloadTypeCode, channelId, videoTitle)
    }

    private suspend fun executePremiumParallel(
        videoUrl: String,
        request: YoutubeDLRequest,
        downloadDir: File,
        notificationBuilder: NotificationCompat.Builder,
        notificationId: Int,
        downloadTypeLabel: String,
        downloadTypeCode: String,
        channelId: String,
        videoTitle: String
    ): Result {
        var bestLang: String? = null
        var isAutomatic = false

        return try {
            var subtitleJob: kotlinx.coroutines.Job? = null
            coroutineScope {
                subtitleJob = launch(Dispatchers.IO) {
                    try {
                        val infoRequest = YoutubeDLRequest(videoUrl)
                        infoRequest.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                        infoRequest.addOption("--extractor-args", "youtube:player-client=android,web")
                        infoRequest.addOption("--dump-json")
                        val infoResponse = YoutubeDL.getInstance().execute(infoRequest) { _, _, _ -> }
                        val jsonStr = infoResponse.out
                        if (!jsonStr.isNullOrBlank()) {
                            val json = Json.parseToJsonElement(jsonStr).jsonObject
                            val subtitles = json["subtitles"]?.jsonObject
                            val autoCaptions = json["automatic_captions"]?.jsonObject

                            if (subtitles != null && subtitles.keys.any { it == "tr" || it.startsWith("tr-") }) {
                                bestLang = subtitles.keys.first { it == "tr" || it.startsWith("tr-") }
                                isAutomatic = false
                            } else if (subtitles != null && subtitles.keys.any { it == "en" || it.startsWith("en-") }) {
                                bestLang = subtitles.keys.first { it == "en" || it.startsWith("en-") }
                                isAutomatic = false
                            } else if (autoCaptions != null && autoCaptions.keys.any { it == "tr" || it.startsWith("tr-") }) {
                                bestLang = autoCaptions.keys.first { it == "tr" || it.startsWith("tr-") }
                                isAutomatic = true
                            } else if (autoCaptions != null && autoCaptions.keys.any { it == "en" || it.startsWith("en-") }) {
                                bestLang = autoCaptions.keys.first { it == "en" || it.startsWith("en-") }
                                isAutomatic = true
                            }
                        }
                        if (bestLang != null) {
                            val subRequest = YoutubeDLRequest(videoUrl)
                            subRequest.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                            subRequest.addOption("--extractor-args", "youtube:player-client=android,web")
                            subRequest.addOption("--skip-download")
                            if (isAutomatic) subRequest.addOption("--write-auto-sub") else subRequest.addOption("--write-subs")
                            subRequest.addOption("--sub-langs", bestLang)
                            subRequest.addOption("--sub-format", "vtt")
                            subRequest.addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
                            YoutubeDL.getInstance().execute(subRequest) { _, _, _ -> }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DownloadWorker", "Parallel subtitle error", e)
                    }
                }

                executeDownload(request, notificationBuilder, notificationId, downloadTypeLabel, downloadTypeCode, downloadDir, null, channelId, videoTitle)
            }
            subtitleJob?.join()

            postDownloadTasks(downloadDir, bestLang, notificationId, notificationBuilder, notificationManager, downloadTypeLabel, downloadTypeCode, channelId, videoTitle)
        } catch (e: Exception) {
            handleDownloadError(notificationId, channelId, downloadTypeLabel)
        }
    }

    private suspend fun executeNormalFlow(
        videoUrl: String,
        request: YoutubeDLRequest,
        downloadDir: File,
        notificationBuilder: NotificationCompat.Builder,
        notificationId: Int,
        downloadTypeLabel: String,
        downloadTypeCode: String,
        channelId: String,
        videoTitle: String
    ): Result {
        var bestLang: String? = null
        var isAutomatic = false

        try {
            val infoRequest = YoutubeDLRequest(videoUrl)
            infoRequest.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            infoRequest.addOption("--extractor-args", "youtube:player-client=android,web")
            infoRequest.addOption("--dump-json")
            val infoResponse = YoutubeDL.getInstance().execute(infoRequest) { _, _, _ -> }
            val jsonStr = infoResponse.out
            if (!jsonStr.isNullOrBlank()) {
                val json = Json.parseToJsonElement(jsonStr).jsonObject
                val subtitles = json["subtitles"]?.jsonObject
                val autoCaptions = json["automatic_captions"]?.jsonObject

                if (subtitles != null && subtitles.keys.any { it == "tr" || it.startsWith("tr-") }) {
                    bestLang = subtitles.keys.first { it == "tr" || it.startsWith("tr-") }
                    isAutomatic = false
                } else if (subtitles != null && subtitles.keys.any { it == "en" || it.startsWith("en-") }) {
                    bestLang = subtitles.keys.first { it == "en" || it.startsWith("en-") }
                    isAutomatic = false
                } else if (autoCaptions != null && autoCaptions.keys.any { it == "tr" || it.startsWith("tr-") }) {
                    bestLang = autoCaptions.keys.first { it == "tr" || it.startsWith("tr-") }
                    isAutomatic = true
                } else if (autoCaptions != null && autoCaptions.keys.any { it == "en" || it.startsWith("en-") }) {
                    bestLang = autoCaptions.keys.first { it == "en" || it.startsWith("en-") }
                    isAutomatic = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Error checking subtitles via dump-json", e)
        }

        if (bestLang != null) {
            try {
                val subRequest = YoutubeDLRequest(videoUrl)
                subRequest.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                subRequest.addOption("--extractor-args", "youtube:player-client=android,web")
                subRequest.addOption("--skip-download")
                if (isAutomatic) subRequest.addOption("--write-auto-sub") else subRequest.addOption("--write-subs")
                subRequest.addOption("--sub-langs", bestLang)
                subRequest.addOption("--sub-format", "vtt")
                subRequest.addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
                YoutubeDL.getInstance().execute(subRequest) { _, _, _ -> }
            } catch (e: Exception) {
                android.util.Log.e("DownloadWorker", "Error downloading subtitle file", e)
            }
        }

        return try {
            executeDownload(request, notificationBuilder, notificationId, downloadTypeLabel, downloadTypeCode, downloadDir, bestLang, channelId, videoTitle)
        } catch (e: Exception) {
            handleDownloadError(notificationId, channelId, downloadTypeLabel)
        }
    }

    private suspend fun executeDownload(
        request: YoutubeDLRequest,
        notificationBuilder: NotificationCompat.Builder,
        notificationId: Int,
        downloadTypeLabel: String,
        downloadTypeCode: String,
        downloadDir: File,
        bestLang: String?,
        channelId: String,
        videoTitle: String
    ): Result {
        YoutubeDL.getInstance().execute(request) { progress, eta, _ ->
            val progressInt = progress.toInt().coerceIn(0, 100)
            setProgressAsync(workDataOf("progress" to progressInt))

            val updatedNotification = notificationBuilder
                .setContentTitle(appContext.getString(R.string.downloaded_format, downloadTypeCode, videoTitle))
                .setContentText(if (progressInt > 0) "${progressInt}% - Kalan: ${eta}s" else "Hazırlanıyor...")
                .setProgress(100, progressInt, false)
                .build()
            notificationManager.notify(notificationId, updatedNotification)
        }

        return postDownloadTasks(downloadDir, bestLang, notificationId, notificationBuilder, notificationManager, downloadTypeLabel, downloadTypeCode, channelId, videoTitle)
    }

    private fun postDownloadTasks(
        downloadDir: File,
        bestLang: String?,
        notificationId: Int,
        notificationBuilder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        downloadTypeLabel: String,
        downloadTypeCode: String,
        channelId: String,
        videoTitle: String
    ): Result {
        val files = downloadDir.listFiles()
        if (files != null) {
            if (bestLang != null) {
                for (file in files) {
                    val name = file.name
                    if (name.endsWith(".$bestLang.vtt")) {
                        val baseName = name.substringBeforeLast(".$bestLang.vtt")
                        val targetFile = File(downloadDir, "$baseName.vtt")
                        if (targetFile.exists()) targetFile.delete()
                        file.renameTo(targetFile)
                    }
                }
            }

            val currentTime = System.currentTimeMillis()
            for (file in files) {
                if (currentTime - file.lastModified() < 30000) {
                    android.media.MediaScannerConnection.scanFile(
                        appContext, arrayOf(file.absolutePath), null
                    ) { path, uri ->
                        android.util.Log.d("DownloadWorker", "Scanned: $path -> $uri")
                    }
                }
            }
        }

        notificationManager.cancel(notificationId)
        val finalNotification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.downloaded_format, downloadTypeCode, videoTitle))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(notificationId + 1, finalNotification)

        return Result.success()
    }

    private fun handleDownloadError(notificationId: Int, channelId: String, downloadTypeLabel: String): Result {
        notificationManager.cancel(notificationId)
        val errorNotification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.unknown_error))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(notificationId + 2, errorNotification)
        return Result.failure()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                appContext.getString(R.string.download_options),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = appContext.getString(R.string.download_screen_title)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
