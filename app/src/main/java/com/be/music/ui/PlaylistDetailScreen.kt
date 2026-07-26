package com.be.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.be.music.R

private enum class PlaylistSort { ALPHA, DURATION, ADDED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: MusicViewModel,
    playlistId: Int,
    navController: NavController,
    onBack: () -> Unit
) {
    val playlists by viewModel.playlistsState.collectAsState()
    val songs by viewModel.songsState.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<com.be.music.data.Song?>(null) }
    var playlistSearchQuery by remember { mutableStateOf("") }
    var showPlayMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showInvertConfirm by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(PlaylistSort.ALPHA) }
    var sortReverse by remember { mutableStateOf(false) }

    BackHandler(enabled = showFullPlayer) { showFullPlayer = false }

    val playlist = remember(playlists, playlistId) { playlists.find { it.id == playlistId } }

    val initialPlaylistSongs = remember(playlist, songs) { playlist?.songIds?.mapNotNull { id -> songs.find { it.id == id } } ?: emptyList() }

    val sortedPlaylistSongs = remember(initialPlaylistSongs, sortMode, sortReverse) {
        val sorted = when (sortMode) {
            PlaylistSort.ALPHA -> initialPlaylistSongs.sortedBy { it.title.lowercase() }
            PlaylistSort.DURATION -> initialPlaylistSongs.sortedBy { it.duration }
            PlaylistSort.ADDED -> initialPlaylistSongs.sortedBy { it.dateModified }
        }
        if (sortReverse) sorted.reversed() else sorted
    }

    val visiblePlaylistSongs = remember(sortedPlaylistSongs, playlistSearchQuery) {
        if (playlistSearchQuery.isBlank()) sortedPlaylistSongs else sortedPlaylistSongs.filter { it.title.contains(playlistSearchQuery, ignoreCase = true) || it.artistName.contains(playlistSearchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: stringResource(R.string.playlist_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(
                        onClick = { showInvertConfirm = true },
                        enabled = playlist != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = stringResource(R.string.invert_playlist)
                        )
                    }
                    IconButton(onClick = { navController.navigate("song_picker/$playlistId") }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_songs)) }
                }
            )
        },
        floatingActionButton = {},
        bottomBar = {
            if (currentSong != null) {
                val position by viewModel.currentPosition.collectAsState()
                val duration by viewModel.duration.collectAsState()
                PersistentMiniPlayer(
                    currentSong = currentSong!!,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    onPlayPause = { viewModel.playPause() },
                    onNext = { viewModel.next() },
                    onPrev = { viewModel.previous() },
                    onClick = { showFullPlayer = true }
                )
            }
        }
    ) { padding ->
        if (visiblePlaylistSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_playlist), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val listState = rememberLazyListState()
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Search + Play + Sort Row
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = playlistSearchQuery,
                        onValueChange = { playlistSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.search) + "...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = { if (playlistSearchQuery.isNotEmpty()) IconButton(onClick = { playlistSearchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = null) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { showPlayMenu = true }) { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_all)) }
                        DropdownMenu(expanded = showPlayMenu, onDismissRequest = { showPlayMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.random_play)) }, onClick = {
                                showPlayMenu = false
                                val shuffled = visiblePlaylistSongs.shuffled()
                                if (shuffled.isNotEmpty()) viewModel.playSong(shuffled.first(), shuffled)
                            }, leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.sequential_play)) }, onClick = {
                                showPlayMenu = false
                                if (visiblePlaylistSongs.isNotEmpty()) viewModel.playSong(visiblePlaylistSongs.first(), visiblePlaylistSongs)
                            }, leadingIcon = { Icon(Icons.Default.Replay, contentDescription = null) })
                        }
                    }
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort)) }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.alphabetical)) }, onClick = { showSortMenu = false; sortMode = PlaylistSort.ALPHA })
                        DropdownMenuItem(text = { Text(stringResource(R.string.song_duration)) }, onClick = { showSortMenu = false; sortMode = PlaylistSort.DURATION })
                        DropdownMenuItem(text = { Text(stringResource(R.string.date_added)) }, onClick = { showSortMenu = false; sortMode = PlaylistSort.ADDED })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(if (!sortReverse) stringResource(R.string.ascending) else stringResource(R.string.descending)) }, onClick = { sortReverse = !sortReverse; showSortMenu = false })
                    }

                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visiblePlaylistSongs) { song ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    val playList = if (playlistSearchQuery.isBlank()) sortedPlaylistSongs else visiblePlaylistSongs
                                    SongListItem(
                                        song = song,
                                        isSelected = false,
                                        isSelectionMode = false,
                                        isPlayingSong = currentSong?.id == song.id,
                                        onClick = { viewModel.playSong(song, playList) },
                                        onSelect = { },
                                        onAddToPlaylist = { },
                                        onDeleteSong = { showDeleteDialog = song },
                                        onPlaySong = { viewModel.playSong(song, playList) }
                                    )
                                }
                            }
                        }
                    }
                    FastScroller(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
                }
            }
        }
    }

    if (showFullPlayer && currentSong != null) {
        FullPlayerScreen(
            viewModel = viewModel,
            song = currentSong!!,
            isPlaying = isPlaying,
            navController = navController,
            onCollapse = { showFullPlayer = false }
        )
    }

    if (showDeleteDialog != null) {
        val targetSong = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_song)) },
            text = { Text(stringResource(R.string.delete_song_confirm, targetSong.title)) },
            confirmButton = {
                Button(onClick = { viewModel.deleteSong(targetSong); showDeleteDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showInvertConfirm && playlist != null) {
        val currentSize = playlist!!.songIds.size
        val totalSongs = remember(songs) { songs.size }
        val newSize = (totalSongs - currentSize).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { showInvertConfirm = false },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
            title = { Text(stringResource(R.string.invert_playlist)) },
            text = {
                Text(stringResource(R.string.invert_playlist_confirm, currentSize, newSize))
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.invertPlaylistSongs(playlist!!)
                    showInvertConfirm = false
                }) {
                    Text(stringResource(R.string.invert))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInvertConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
