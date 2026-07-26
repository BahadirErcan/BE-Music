package com.be.music.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.be.music.data.*
import com.be.music.R
import com.be.music.player.PlaybackService
import com.be.music.youtube.YoutubeSearcher
import com.be.music.youtube.YoutubeVideo
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SortOrder { NAME, DATE, DURATION }
enum class ThemeStyle { LIGHT, DARK, SYSTEM }

@HiltViewModel
class MusicViewModel @Inject constructor(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

    private val sharingStarted = SharingStarted.WhileSubscribed(5000)

    val filterSettingsState: StateFlow<FilterSettings> = repository.filterSettings
        .stateIn(viewModelScope, sharingStarted, FilterSettings())

    val themeStyle: StateFlow<ThemeStyle> = repository.filterSettings
        .map { settings ->
            when (settings.themeMode) {
                "LIGHT" -> ThemeStyle.LIGHT
                "DARK" -> ThemeStyle.DARK
                else -> ThemeStyle.SYSTEM
            }
        }.stateIn(viewModelScope, sharingStarted, ThemeStyle.SYSTEM)

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NAME)
    val sortOrder = _sortOrder.asStateFlow()

    private val _sortReverse = MutableStateFlow(true)
    val sortReverse = _sortReverse.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _navigationBarHeight = MutableStateFlow(0)
    val navigationBarHeight = _navigationBarHeight.asStateFlow()

    private val _isThreeButtonNav = MutableStateFlow(false)
    val isThreeButtonNav = _isThreeButtonNav.asStateFlow()

    fun updateNavigationBarHeight(height: Int) {
        if (_navigationBarHeight.value != height) {
            _navigationBarHeight.value = height
        }
    }

    fun updateIsThreeButtonNav(isThreeButton: Boolean) {
        if (_isThreeButtonNav.value != isThreeButton) {
            _isThreeButtonNav.value = isThreeButton
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val songsState: StateFlow<List<Song>> = kotlinx.coroutines.flow.combine(repository.filteredSongs, searchQuery, sortOrder, sortReverse) { list, query, order, reverse ->
        var filtered = if (query.isBlank()) list else list.filter { it.title.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true) }

        filtered = when (order) {
            SortOrder.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.DATE -> filtered.sortedByDescending { it.dateModified }
            SortOrder.DURATION -> filtered.sortedByDescending { it.duration }
        }

        if (reverse) filtered = filtered.reversed()
        filtered
    }.stateIn(viewModelScope, sharingStarted, emptyList())

    val artistsState: StateFlow<List<Artist>> = kotlinx.coroutines.flow.combine(repository.allArtists, searchQuery, sortOrder, sortReverse) { list, query, order, reverse ->
        val filtered = if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
        var sorted = when (order) {
            SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
        if (reverse) sorted = sorted.reversed()
        sorted
    }.stateIn(viewModelScope, sharingStarted, emptyList())

    val albumsState: StateFlow<List<Album>> = kotlinx.coroutines.flow.combine(repository.allAlbums, searchQuery, sortOrder, sortReverse) { list, query, order, reverse ->
        val filtered = if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true) }
        var sorted = when (order) {
            SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
        if (reverse) sorted = sorted.reversed()
        sorted
    }.stateIn(viewModelScope, sharingStarted, emptyList())

    val playlistsState: StateFlow<List<Playlist>> = kotlinx.coroutines.flow.combine(repository.allPlaylists, searchQuery, sortOrder, sortReverse) { list, query, order, reverse ->
        val filtered = if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
        var sorted = when (order) {
            SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
        if (reverse) sorted = sorted.reversed()
        sorted
    }.stateIn(viewModelScope, sharingStarted, emptyList())

    // (Downloads removed) YouTube/search/download state removed

    // Media3 State
    private var mediaController: MediaController? = null
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    // Madde 14: Sonraki şarkı bilgisi
    private val _nextSong = MutableStateFlow<Song?>(null)
    val nextSong = _nextSong.asStateFlow()

    val shuffleMode = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemaining = MutableStateFlow(0L)
    val sleepTimerRemaining = _sleepTimerRemaining.asStateFlow()
    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive = _sleepTimerActive.asStateFlow()

    // New Lyrics States
    private val _lyricSearchTargetSong = MutableStateFlow<Song?>(null)
    val lyricSearchTargetSong = _lyricSearchTargetSong.asStateFlow()

    private val _lyricsSearchResults = MutableStateFlow<List<YoutubeVideo>>(emptyList())
    val lyricsSearchResults = _lyricsSearchResults.asStateFlow()
    private val _lyricsSearchLoading = MutableStateFlow(false)
    val lyricsSearchLoading = _lyricsSearchLoading.asStateFlow()
    private val _lyricsSearchError = MutableStateFlow<String?>(null)
    val lyricsSearchError = _lyricsSearchError.asStateFlow()

    private val _availableSubtitles = MutableStateFlow<List<SubtitleOption>>(emptyList())
    val availableSubtitles = _availableSubtitles.asStateFlow()
    private val _checkingSubtitles = MutableStateFlow(false)
    val checkingSubtitles = _checkingSubtitles.asStateFlow()
    private val _subtitlesError = MutableStateFlow<String?>(null)
    val subtitlesError = _subtitlesError.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<com.be.music.youtube.LyricLine>>(emptyList())
    val currentLyrics = _currentLyrics.asStateFlow()

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            setupControllerListener()
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener() {
        val controller = mediaController ?: return
        repeatMode.value = controller.repeatMode
        controller.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull()
                viewModelScope.launch {
                    val song = id?.let { repository.getSongsByIds(listOf(it)).firstOrNull() }
                    _currentSong.value = song
                    loadLyricsForSong(song)
                    updateNextSong()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressTracker() else stopProgressTracker()
            }
            override fun onRepeatModeChanged(newRepeatMode: Int) {
                repeatMode.value = newRepeatMode
                updateNextSong()
            }
        })
    }

    // Madde 14: Sonraki şarkıyı güncelle
    private fun updateNextSong() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        val itemCount = controller.mediaItemCount
        if (currentIndex < itemCount - 1) {
            val nextItem = controller.getMediaItemAt(currentIndex + 1)
            val nextId = nextItem.mediaId.toLongOrNull()
            viewModelScope.launch {
                _nextSong.value = nextId?.let { repository.getSongsByIds(listOf(it)).firstOrNull() }
            }
        } else {
            _nextSong.value = null
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                _currentPosition.value = mediaController?.currentPosition ?: 0L
                _duration.value = mediaController?.duration ?: 0L
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() { progressJob?.cancel() }

    // Madde 4: MediaItem'a metadata ekleyerek bildirimde şarkı adı, sanatçı, albüm art gösterilmesini sağla
    fun playSong(song: Song, list: List<Song>) {
        val controller = mediaController ?: return
        val items = list.map { s ->
            val artUri = try {
                android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, s.id
                )
            } catch (e: Exception) { null }

            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.uriString)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artistName)
                        .setAlbumTitle(s.albumName)
                        .setArtworkUri(artUri)
                        .build()
                )
                .build()
        }
        val index = list.indexOf(song).coerceAtLeast(0)
        controller.setMediaItems(items, index, 0L)
        controller.prepare()
        controller.play()
        // If playing a list (playlist) enable repeat-all so the playlist loops
        if (items.size > 1) {
            controller.repeatMode = Player.REPEAT_MODE_ALL
            repeatMode.value = Player.REPEAT_MODE_ALL
        }
    }

    fun playPause() {
        if (mediaController?.isPlaying == true) mediaController?.pause() else mediaController?.play()
    }

    fun next() = mediaController?.seekToNext()
    fun previous() = mediaController?.seekToPrevious()
    fun seekTo(pos: Long) = mediaController?.seekTo(pos)
    fun toggleShuffle() {
        mediaController?.shuffleModeEnabled = !(mediaController?.shuffleModeEnabled ?: false)
        shuffleMode.value = mediaController?.shuffleModeEnabled ?: false
        updateNextSong()
    }

    fun toggleRepeat() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        repeatMode.value = nextMode
        updateNextSong()
    }

    fun setTab(index: Int) { _selectedTab.value = index }
    fun scanMusic(force: Boolean) = viewModelScope.launch { repository.scanLocalMusic(force) }

    // Playlist Management
    fun createPlaylist(name: String, songIds: List<Long>, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val playlist = repository.createPlaylist(name, songIds)
            onCreated(playlist.id)
        }
    }

    fun updatePlaylistSongs(playlist: Playlist, songIds: List<Long>) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist.copy(songIds = songIds))
        }
    }

    fun updatePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlist: Playlist, songId: Long) {
        viewModelScope.launch {
            val updatedIds = (playlist.songIds + songId).distinct()
            repository.updatePlaylist(playlist.copy(songIds = updatedIds))
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { repository.deletePlaylist(playlist) }
    }

    fun invertPlaylistSongs(playlist: Playlist) {
        viewModelScope.launch {
            val currentIds = playlist.songIds.toSet()
            val allSongIds = songsState.value.map { it.id }
            val invertedIds = allSongIds.filter { it !in currentIds }
            repository.updatePlaylist(playlist.copy(songIds = invertedIds))
        }
    }

    fun importPlaylists() {
        viewModelScope.launch { repository.importPlaylistsFromJson() }
    }

    fun importPlaylistFromUri(uri: android.net.Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (success, message) = repository.importPlaylistFromUri(uri)
            onComplete(success, message)
        }
    }

    fun exportPlaylist(playlist: Playlist, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val path = repository.exportPlaylistFilePath(playlist)
            onComplete(path != null, path)
        }
    }

    // Settings & Selection
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds = _selectedSongIds.asStateFlow()

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) _selectedSongIds.value = emptySet()
    }

    fun toggleSongSelection(songId: Long) {
        val current = _selectedSongIds.value.toMutableSet()
        if (current.contains(songId)) current.remove(songId) else current.add(songId)
        _selectedSongIds.value = current
    }

    fun clearSelection() {
        _selectedSongIds.value = emptySet()
        _isSelectionMode.value = false
    }

    // Madde 8: Sanatçı/Albüm bazlı toplu seçim
    fun toggleArtistSelection(artistSongs: List<Song>) {
        val ids = artistSongs.map { it.id }.toSet()
        val current = _selectedSongIds.value
        if (current.containsAll(ids)) {
            // Tümü zaten seçili, hepsini kaldır
            _selectedSongIds.value = current - ids
        } else {
            // Hepsini ekle
            _selectedSongIds.value = current + ids
        }
    }

    fun toggleAlbumSelection(albumSongs: List<Song>) {
        val ids = albumSongs.map { it.id }.toSet()
        val current = _selectedSongIds.value
        if (current.containsAll(ids)) {
            _selectedSongIds.value = current - ids
        } else {
            _selectedSongIds.value = current + ids
        }
    }

    // Madde 9: Sanatçı/Albüm tüm şarkılarını repeat modunda çal
    fun playArtistSongs(artistSongs: List<Song>) {
        if (artistSongs.isEmpty()) return
        val controller = mediaController ?: return
        controller.repeatMode = Player.REPEAT_MODE_ALL
        repeatMode.value = Player.REPEAT_MODE_ALL
        playSong(artistSongs.first(), artistSongs)
    }

    fun playAlbumSongs(albumSongs: List<Song>) {
        if (albumSongs.isEmpty()) return
        val controller = mediaController ?: return
        controller.repeatMode = Player.REPEAT_MODE_ALL
        repeatMode.value = Player.REPEAT_MODE_ALL
        playSong(albumSongs.first(), albumSongs)
    }

    fun setTheme(theme: ThemeStyle) {
        viewModelScope.launch {
            val current = filterSettingsState.value
            val newMode = when (theme) {
                ThemeStyle.LIGHT -> "LIGHT"
                ThemeStyle.DARK -> "DARK"
                ThemeStyle.SYSTEM -> "SYSTEM"
            }
            repository.saveFilterSettings(current.copy(themeMode = newMode))
        }
    }

    fun setLanguage(langCode: String) {
        viewModelScope.launch {
            val current = filterSettingsState.value
            repository.saveFilterSettings(current.copy(language = langCode))
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun toggleSortReverse() {
        _sortReverse.value = !_sortReverse.value
    }

    // Youtube/search/download functionality removed

    fun updateFilterSettings(settings: FilterSettings) {
        viewModelScope.launch { repository.saveFilterSettings(settings) }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerActive.value = false
            _sleepTimerRemaining.value = 0L
            return
        }

        _sleepTimerActive.value = true
        _sleepTimerRemaining.value = minutes * 60L

        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerRemaining.value > 0) {
                delay(1000)
                _sleepTimerRemaining.value = _sleepTimerRemaining.value - 1
            }
            _sleepTimerActive.value = false
            mediaController?.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerActive.value = false
        _sleepTimerRemaining.value = 0L
    }

    fun searchLyricsForSong(song: Song) {
        _lyricSearchTargetSong.value = song
        viewModelScope.launch {
            _lyricsSearchLoading.value = true
            _lyricsSearchError.value = null
            _lyricsSearchResults.value = emptyList()
            try {
                val artist = if (song.artistName.contains("unknown", ignoreCase = true)) "" else "${song.artistName} "
                val query = "$artist${song.title}".trim()
                val results = withContext(Dispatchers.IO) {
                    YoutubeSearcher().search(query)
                }
                val top10 = results.take(10)
                _lyricsSearchResults.value = top10
                if (top10.isEmpty()) {
                    _lyricsSearchError.value = getApplication<Application>().getString(R.string.lyrics_not_found)
                }
            } catch (e: Exception) {
                _lyricsSearchError.value = e.message ?: getApplication<Application>().getString(R.string.lyrics_error)
            } finally {
                _lyricsSearchLoading.value = false
            }
        }
    }

    fun fetchLyricsForCurrentSong() {
        val current = _currentSong.value ?: return
        searchLyricsForSong(current)
    }

    fun checkSubtitlesForVideo(videoId: String) {
        viewModelScope.launch {
            _checkingSubtitles.value = true
            _subtitlesError.value = null
            _availableSubtitles.value = emptyList()
            try {
                val options = withContext(Dispatchers.IO) {
                    val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                    val request = YoutubeDLRequest(videoUrl)
                    request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    request.addOption("--extractor-args", "youtube:player-client=android,web")
                    request.addOption("--dump-json")
                    
                    val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request) { _, _, _ -> }
                    val jsonStr = response.out
                    if (jsonStr.isNullOrBlank()) {
                        throw Exception(getApplication<Application>().getString(R.string.video_info_error))
                    }
                    val json = Json.parseToJsonElement(jsonStr).jsonObject
                    val subtitlesJson = json["subtitles"]?.jsonObject
                    val autoCaptionsJson = json["automatic_captions"]?.jsonObject
                    val optionsList = mutableListOf<SubtitleOption>()
                    
                    fun getLanguageName(code: String): String {
                        return when {
                            code.startsWith("tr") -> "Türkçe"
                            code.startsWith("en") -> "İngilizce"
                            code.startsWith("de") -> "Almanca"
                            code.startsWith("fr") -> "Fransızca"
                            code.startsWith("es") -> "İspanyolca"
                            code.startsWith("it") -> "İtalyanca"
                            code.startsWith("ru") -> "Rusça"
                            code.startsWith("ja") -> "Japonca"
                            code.startsWith("ko") -> "Korece"
                            code.startsWith("zh") -> "Çince"
                            code.startsWith("az") -> "Azerbaycanca"
                            else -> code.uppercase()
                        }
                    }
                    
                    val manualSubtitles = subtitlesJson?.keys?.map { key ->
                        SubtitleOption(key, getLanguageName(key), isAutomatic = false)
                    } ?: emptyList()
                    
                    val autoSubtitles = autoCaptionsJson?.keys?.map { key ->
                        SubtitleOption(key, getLanguageName(key), isAutomatic = true)
                    } ?: emptyList()
                    
                    if (manualSubtitles.isNotEmpty()) {
                        manualSubtitles.sortedBy { it.name }
                    } else {
                        autoSubtitles.sortedBy { it.name }
                    }
                }
                _availableSubtitles.value = options
                if (options.isEmpty()) {
                    _subtitlesError.value = getApplication<Application>().getString(R.string.lyrics_not_found_for_song)
                }
            } catch (e: Exception) {
                _subtitlesError.value = e.message ?: getApplication<Application>().getString(R.string.subtitle_check_error)
            } finally {
                _checkingSubtitles.value = false
            }
        }
    }

    fun downloadSelectedSubtitle(videoId: String, option: SubtitleOption, callback: (Boolean) -> Unit) {
        val targetSong = _lyricSearchTargetSong.value ?: _currentSong.value
        if (targetSong == null) {
            _subtitlesError.value = getApplication<Application>().getString(R.string.target_song_not_found)
            callback(false)
            return
        }
        viewModelScope.launch {
            _lyricsSearchLoading.value = true
            _subtitlesError.value = null
            try {
                val success = withContext(Dispatchers.IO) {
                    val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                    val request = YoutubeDLRequest(videoUrl)
                    request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    request.addOption("--extractor-args", "youtube:player-client=android,web")
                    request.addOption("--skip-download")
                    if (option.isAutomatic) {
                        request.addOption("--write-auto-sub")
                    } else {
                        request.addOption("--write-subs")
                    }
                    request.addOption("--sub-langs", option.code)
                    request.addOption("--sub-format", "vtt")
                    
                    val songFile = File(targetSong.path)
                    val parentDir = songFile.parentFile ?: File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "")
                    val songBaseName = songFile.nameWithoutExtension
                    
                    request.addOption("-o", "${parentDir.absolutePath}/$songBaseName.%(ext)s")
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request) { _, _, _ -> }
                    
                    val downloadedSubFile = File(parentDir, "$songBaseName.${option.code}.vtt")
                    val targetSubFile = File(parentDir, "$songBaseName.vtt")
                    
                    if (downloadedSubFile.exists()) {
                        if (targetSubFile.exists()) targetSubFile.delete()
                        if (downloadedSubFile.renameTo(targetSubFile)) {
                            repository.updateSongLyricsPath(targetSong, targetSubFile.absolutePath)
                            true
                        } else {
                            false
                        }
                    } else {
                        val directFile = File(parentDir, "$songBaseName.vtt")
                        if (directFile.exists()) {
                            repository.updateSongLyricsPath(targetSong, directFile.absolutePath)
                            true
                        } else {
                            val possibleFiles = parentDir.listFiles { _, name ->
                                name.startsWith(songBaseName) && name.endsWith(".vtt") && name != "$songBaseName.vtt"
                            }
                            val matchedFile = possibleFiles?.firstOrNull()
                            if (matchedFile != null) {
                                if (targetSubFile.exists()) targetSubFile.delete()
                                if (matchedFile.renameTo(targetSubFile)) {
                                    repository.updateSongLyricsPath(targetSong, targetSubFile.absolutePath)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                    }
                }
                if (success) {
                    val updatedSong = repository.getSongsByIds(listOf(targetSong.id)).firstOrNull()
                    if (updatedSong != null) {
                        if (_currentSong.value?.id == targetSong.id) {
                            _currentSong.value = updatedSong
                        }
                        loadLyricsForSong(updatedSong)
                    }
                    callback(true)
                } else {
                    _subtitlesError.value = getApplication<Application>().getString(R.string.subtitle_save_error)
                    callback(false)
                }
            } catch (e: Exception) {
                _subtitlesError.value = e.message ?: getApplication<Application>().getString(R.string.subtitle_download_error)
                callback(false)
            } finally {
                _lyricsSearchLoading.value = false
            }
        }
    }

    fun cancelLyricsDownload() {
        _lyricsSearchLoading.value = false
        _checkingSubtitles.value = false
        _subtitlesError.value = null
    }

    private fun loadLyricsForSong(song: Song?) {
        if (song == null) {
            _currentLyrics.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = song.lyricsPath ?: run {
                    val basePath = song.path.substringBeforeLast('.')
                    val vttFile = File("$basePath.vtt")
                    if (vttFile.exists()) vttFile.absolutePath else null
                }
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        val content = file.readText()
                        val parsed = com.be.music.youtube.VttParser.parse(content)
                        _currentLyrics.value = parsed
                        return@launch
                    }
                }
                _currentLyrics.value = emptyList()
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Error loading lyrics", e)
                _currentLyrics.value = emptyList()
            }
        }
    }

    // Madde 6: Şarkı meta verisi düzenleme
    fun updateSongMetadata(song: Song, title: String, artist: String, album: String) {
        viewModelScope.launch {
            repository.updateSongMetadata(song, title, artist, album)
            val updatedSong = song.copy(title = title, artistName = artist, albumName = album)
            updateCurrentMediaItemMetadata(updatedSong)
            // Eğer şu an çalan şarkıysa, currentSong state'ini güncelle
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = updatedSong
                loadLyricsForSong(updatedSong)
            }
        }
    }

    // Madde 15: Şarkı silme
    private fun updateCurrentMediaItemMetadata(song: Song) {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        if (currentItem.mediaId != song.id.toString()) return

        val artUri = try {
            android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                song.id
            )
        } catch (e: Exception) {
            null
        }

        val updatedItem = currentItem.buildUpon()
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumName)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
        controller.replaceMediaItem(controller.currentMediaItemIndex, updatedItem)
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            repository.deleteSong(song)
        }
    }
}

data class SubtitleOption(
    val code: String,
    val name: String,
    val isAutomatic: Boolean
)
