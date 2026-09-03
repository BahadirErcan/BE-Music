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
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<com.be.music.data.Song?>(null) }
    var showRemoveFromPlaylistDialog by remember { mutableStateOf<com.be.music.data.Song?>(null) }
    var playlistSearchQuery by remember { mutableStateOf("") }
    var showPlayMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showInvertConfirm by remember { mutableStateOf(false) }
    var showPlaylistCreateDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(PlaylistSort.ALPHA) }
    var sortReverse by remember { mutableStateOf(false) }

    BackHandler(enabled = showFullPlayer || isSelectionMode) {
        when {
            showFullPlayer -> showFullPlayer = false
            isSelectionMode -> viewModel.clearSelection()
        }
    }

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
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.select_song) + " (${selectedSongIds.size})")
                    } else {
                        Text(playlist?.name ?: stringResource(R.string.playlist_label))
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    } else {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { showRemoveDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_mode))
                        }
                        IconButton(onClick = { if (selectedSongIds.isNotEmpty()) showPlaylistCreateDialog = true }) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = stringResource(R.string.create_playlist_btn))
                        }
                    }
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
                                        isSelected = selectedSongIds.contains(song.id),
                                        isSelectionMode = isSelectionMode,
                                        isPlayingSong = currentSong?.id == song.id,
                                        onClick = { viewModel.playSong(song, playList) },
                                        onSelect = { viewModel.toggleSongSelection(song.id) },
                                        onAddToPlaylist = { },
                                        onDeleteSong = { showDeleteDialog = song },
                                        onPlaySong = { viewModel.playSong(song, playList) },
                                        onRemoveFromPlaylist = { showRemoveFromPlaylistDialog = song }
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

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.remove_selected, selectedSongIds.size)) },
            confirmButton = {
                Button(onClick = { viewModel.deleteSelectedSongs(); showRemoveDialog = false }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    if (showPlaylistCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        val initialSelected = remember { selectedSongIds.toList() }
        AlertDialog(
            onDismissRequest = { showPlaylistCreateDialog = false },
            title = { Text(stringResource(R.string.create_new_playlist)) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (playlistName.isNotBlank()) {
                        viewModel.createPlaylist(playlistName, initialSelected) {
                            showPlaylistCreateDialog = false
                            viewModel.clearSelection()
                            navController.navigate("song_picker/$it")
                        }
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { showPlaylistCreateDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showRemoveFromPlaylistDialog != null) {
        val targetSong = showRemoveFromPlaylistDialog!!
        AlertDialog(
            onDismissRequest = { showRemoveFromPlaylistDialog = null },
            title = { Text(stringResource(R.string.remove_from_playlist)) },
            text = { Text(stringResource(R.string.remove_from_playlist_confirm, targetSong.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeSongFromPlaylist(playlistId, targetSong.id)
                        showRemoveFromPlaylistDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { showRemoveFromPlaylistDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
