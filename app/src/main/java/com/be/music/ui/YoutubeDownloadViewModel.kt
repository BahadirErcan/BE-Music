package com.be.music.ui

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.be.music.BeMusicApplication
import com.be.music.R
import com.be.music.premium.PremiumManager
import com.be.music.premium.PremiumState
import com.be.music.youtube.BatchDownloadWorker
import com.be.music.youtube.DownloadWorker
import com.be.music.youtube.SpotifyDownloadWorker
import com.be.music.youtube.SpotifyPlaylistParser
import com.be.music.youtube.YoutubeSearcher
import com.be.music.youtube.YoutubeVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class YoutubeDownloadViewModel @Inject constructor(
    private val premiumManager: PremiumManager
) : ViewModel() {
    private val searcher = YoutubeSearcher()

    private val _searchResults = MutableStateFlow<List<YoutubeVideo>>(emptyList())
    val searchResults: StateFlow<List<YoutubeVideo>> = _searchResults

    private val _visibleItemsCount = MutableStateFlow(18)
    val visibleItemsCount: StateFlow<Int> = _visibleItemsCount.asStateFlow()

    private val _displayedSearchResults = combine(_searchResults, _visibleItemsCount) { results, count ->
        results.take(count)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val displayedSearchResults: StateFlow<List<YoutubeVideo>> = _displayedSearchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Composite key: "videoId_type" (e.g. "abc123_m4a", "abc123_mp4")
    private val _downloadingVideos = MutableStateFlow<Set<String>>(emptySet())
    val downloadingVideos: StateFlow<Set<String>> = _downloadingVideos

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress

    // Spotify playlist indirme durumu
    private val _spotifyDownloading = MutableStateFlow(false)
    val spotifyDownloading: StateFlow<Boolean> = _spotifyDownloading.asStateFlow()

    private val _spotifyError = MutableStateFlow<String?>(null)
    val spotifyError: StateFlow<String?> = _spotifyError.asStateFlow()

    private val _spotifyStatus = MutableStateFlow<String?>(null)
    val spotifyStatus: StateFlow<String?> = _spotifyStatus.asStateFlow()

    // Batch (manuel liste) indirme durumu
    private val _batchDownloading = MutableStateFlow(false)
    val batchDownloading: StateFlow<Boolean> = _batchDownloading.asStateFlow()

    private val _batchError = MutableStateFlow<String?>(null)
    val batchError: StateFlow<String?> = _batchError.asStateFlow()

    private val _batchStatus = MutableStateFlow<String?>(null)
    val batchStatus: StateFlow<String?> = _batchStatus.asStateFlow()

    // Rewarded Ad sayaç
    private val _downloadCount = MutableStateFlow(0)
    val downloadCount: StateFlow<Int> = _downloadCount.asStateFlow()

    private val _showAdEvent = MutableSharedFlow<Unit>()
    val showAdEvent: SharedFlow<Unit> = _showAdEvent.asSharedFlow()

    val premiumState: StateFlow<PremiumState> = premiumManager.state

    init {
        _downloadCount.value = BeMusicApplication.rewardedAdManager.downloadCount
    }

    fun loadMoreResults() {
        val currentCount = _visibleItemsCount.value
        if (currentCount < _searchResults.value.size) {
            _visibleItemsCount.value = (currentCount + 18).coerceAtMost(_searchResults.value.size)
        }
    }

    fun searchYouTube(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            val results = performYoutubeSearch(query)
            _searchResults.value = results
            _visibleItemsCount.value = 18
            _isLoading.value = false
        }
    }

    private suspend fun performYoutubeSearch(query: String): List<YoutubeVideo> {
        return withContext(Dispatchers.IO) {
            searcher.search(query)
        }
    }

    fun startDownload(context: Context, videoId: String, videoTitle: String, type: String, quality: String, downloadLyrics: Boolean) {
        val compositeKey = "${videoId}_${type}"
        _downloadingVideos.value = _downloadingVideos.value + compositeKey
        _downloadProgress.value = _downloadProgress.value + (compositeKey to 0)

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    "video_id" to videoId,
                    "video_title" to videoTitle,
                    "type" to type,
                    "quality" to quality,
                    "lyrics" to downloadLyrics
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_${compositeKey}",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                _downloadingVideos.value = _downloadingVideos.value - compositeKey
                _downloadProgress.value = _downloadProgress.value - compositeKey


                if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    val newCount = BeMusicApplication.rewardedAdManager.incrementDownloadCount()
                    _downloadCount.value = newCount
                    if (BeMusicApplication.rewardedAdManager.shouldShowAd()) {
                        viewModelScope.launch { _showAdEvent.emit(Unit) }

                    }
                }
            } else if (workInfo != null) {
                val progress = workInfo.progress.getInt("progress", 0)
                _downloadProgress.value = _downloadProgress.value + (compositeKey to progress)
            }
        }
    }

    fun isSpotifyUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.contains("open.spotify.com/") ||
            trimmed.contains("spotify.com/playlist/") ||
            trimmed.contains("spotify.com/track/") ||
            trimmed.contains("spotify.link/") ||
            trimmed.contains("spotify:playlist:") ||
            trimmed.contains("spotify:track:")
    }

    fun isSpotifyPlaylistUrl(url: String): Boolean {
        return isSpotifyUrl(url)
    }

    suspend fun resolveBatchInputLines(rawLines: List<String>): List<String> = withContext(Dispatchers.IO) {
        val resolvedLines = mutableListOf<String>()
        rawLines.forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) return@forEach
            if (isSpotifyUrl(trimmed)) {
                resolvedLines.addAll(SpotifyPlaylistParser.parseUrl(trimmed).map { "${it.name} - ${it.artist}" })
            } else {
                resolvedLines.add(trimmed)
            }
        }
        resolvedLines.distinct()
    }

    fun startSpotifyPlaylistDownload(context: Context, url: String, quality: String, downloadLyrics: Boolean) {
        _spotifyDownloading.value = true
        _spotifyError.value = null
        _spotifyStatus.value = context.getString(R.string.spotify_fetching_playlist)

        val workRequest = OneTimeWorkRequestBuilder<SpotifyDownloadWorker>()
            .setInputData(
                workDataOf(
                    "playlist_url" to url,
                    "quality" to quality,
                    "lyrics" to downloadLyrics
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                _spotifyDownloading.value = false
                if(!PremiumManager.isPremiumStatic){
                    _downloadCount.value = BeMusicApplication.rewardedAdManager.downloadCount}
                if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    if (BeMusicApplication.rewardedAdManager.shouldShowAd()) {
                        viewModelScope.launch { _showAdEvent.emit(Unit) }
                    }
                }
                if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                    val error = workInfo.outputData.getString("error")
                    _spotifyError.value = error ?: context.getString(R.string.unknown_error)
                    _spotifyStatus.value = null
                } else {
                    val downloaded = workInfo.outputData.getInt("downloaded", 0)
                    val total = workInfo.outputData.getInt("total", 0)
                    val failed = workInfo.outputData.getInt("failed", 0)
                    _spotifyStatus.value = if (failed > 0) {
                        context.getString(R.string.download_progress_with_fail, downloaded, total, failed)
                    } else {
                        context.getString(R.string.download_progress_success, downloaded, total)
                    }
                }
            } else if (workInfo != null) {
                val downloaded = workInfo.progress.getInt("downloaded", 0)
                val total = workInfo.progress.getInt("total", 0)
                val currentTrack = workInfo.progress.getString("current_track") ?: ""
                val progress = workInfo.progress.getInt("progress", 0)
                if (total > 0) {
                    _spotifyStatus.value = "$downloaded/$total | $currentTrack %$progress"
                }
            }
        }
    }

    fun clearSpotifyError() {
        _spotifyError.value = null
    }

    fun clearSpotifyStatus() {
        _spotifyStatus.value = null
    }

    fun startBatchDownload(context: Context, trackLines: List<String>, quality: String, downloadLyrics: Boolean, parallelLimit: Int = 3) {
        val validLines = trackLines.filter { it.isNotBlank() }
        if (validLines.isEmpty()) {
            _batchError.value = context.getString(R.string.batch_list_empty)
            return
        }

        _batchDownloading.value = true
        _batchError.value = null
        _batchStatus.value = context.getString(R.string.batch_preparing)

        val workRequest = OneTimeWorkRequestBuilder<BatchDownloadWorker>()
            .setInputData(
                workDataOf(
                    "track_list" to validLines.joinToString("\n"),
                    "quality" to quality,
                    "lyrics" to downloadLyrics,
                    "parallel_limit" to parallelLimit
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                _batchDownloading.value = false
                _downloadCount.value = BeMusicApplication.rewardedAdManager.downloadCount
                if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    if (BeMusicApplication.rewardedAdManager.shouldShowAd()) {
                        viewModelScope.launch { _showAdEvent.emit(Unit) }
                    }
                }
                if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                    val error = workInfo.outputData.getString("error")
                    _batchError.value = error ?: context.getString(R.string.unknown_error)
                    _batchStatus.value = null
                } else {
                    val downloaded = workInfo.outputData.getInt("downloaded", 0)
                    val total = workInfo.outputData.getInt("total", 0)
                    val failed = workInfo.outputData.getInt("failed", 0)
                    _batchStatus.value = if (failed > 0) {
                        context.getString(R.string.download_progress_with_fail, downloaded, total, failed)
                    } else {
                        context.getString(R.string.download_progress_success, downloaded, total)
                    }
                }
            } else if (workInfo != null) {
                val downloaded = workInfo.progress.getInt("downloaded", 0)
                val total = workInfo.progress.getInt("total", 0)
                val currentTrack = workInfo.progress.getString("current_track") ?: ""
                val progress = workInfo.progress.getInt("progress", 0)
                if (total > 0) {
                    _batchStatus.value = "$downloaded/$total | $currentTrack %$progress"
                }
            }
        }
    }

    fun clearBatchError() {
        _batchError.value = null
    }

    fun clearBatchStatus() {
        _batchStatus.value = null
    }

    fun cancelDownload(context: Context, compositeKey: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$compositeKey")
        _downloadingVideos.value = _downloadingVideos.value - compositeKey
        _downloadProgress.value = _downloadProgress.value - compositeKey
    }

    fun cancelSpotifyDownload(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("spotify_download")
        _spotifyDownloading.value = false
        _spotifyStatus.value = null
    }

    fun cancelBatchDownload(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("batch_download")
        _batchDownloading.value = false
        _batchStatus.value = null
    }

    fun showRewardedAd(activity: Activity) {
        BeMusicApplication.rewardedAdManager.showAd(activity, onRewardEarned = {
            viewModelScope.launch {
                premiumManager.onRewardedAdCompleted()
            }
        })
    }

    fun isQualityPremium(quality: String): Boolean {
        return premiumManager.isQualityPremium(quality) && !premiumManager.isPremium
    }
}
