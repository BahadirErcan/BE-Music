package com.be.music.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.be.music.data.Playlist
import com.be.music.data.Song
import androidx.navigation.NavController
import com.be.music.R
import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast.makeText
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMusicScreen(viewModel: MusicViewModel, navController: NavController) {
    val context = LocalContext.current

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.scanMusic(force = true)
            } else {
                makeText(context, "Tüm dosyalara erişim izni verilmedi. Silme ve indirme işlemleri çalışmayabilir.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            viewModel.scanMusic(force = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
        } else {
            makeText(context, "Storage permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            viewModel.scanMusic(force = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    val songs by viewModel.songsState.collectAsState()
    val playlists by viewModel.playlistsState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val sortReverse by viewModel.sortReverse.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()

    var showPlaylistCreateDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Song?>(null) }
    var importMessage by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPlaylistFromUri(uri) { success, message ->
                importMessage = message
                makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = showFullPlayer || isSelectionMode || selectedTab != 0) {
        when {
            showFullPlayer -> showFullPlayer = false
            isSelectionMode -> viewModel.clearSelection()
            else -> viewModel.setTab(0)
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            "BE MUSIC", 
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { 
                                scope.launch {
                                    isRefreshing = true
                                    viewModel.scanMusic(force = true)
                                    delay(800)
                                    isRefreshing = false
                                }
                            }
                        ) 
                    },
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon( Icons.Default.Close, contentDescription = null)
                            }
                        }
                        else {
                            Row {
                                IconButton(onClick = { navController.navigate("youtube_download") }) {
                                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { navController.navigate("premium") }) {
                                    Icon(Icons.Default.Star, contentDescription = stringResource(R.string.premium_button), tint = androidx.compose.ui.graphics.Color(0xFFFFC107))
                                }
                            }
                        }

                    },


                    actions = {
                        var showMainDropdown by remember { mutableStateOf(false) }
                        var showSortSubmenu by remember { mutableStateOf(false) }
                        var showRemoveDialog by remember { mutableStateOf(false) }

                        if (isSelectionMode) {
                            IconButton(onClick = { showRemoveDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_mode))
                            }
                            IconButton(onClick = { if (selectedSongIds.isNotEmpty()) showPlaylistCreateDialog = true }) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = stringResource(R.string.create_playlist_btn))
                            }
                        }

                        Box {
                            IconButton(onClick = { showMainDropdown = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_options))
                            }
                            DropdownMenu(
                                expanded = showMainDropdown,
                                onDismissRequest = { showMainDropdown = false; showSortSubmenu = false }
                            ) {
                                if (!showSortSubmenu) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.history)) },
                                        onClick = { navController.navigate("history"); showMainDropdown = false },
                                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                                    )
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort)) },
                                        onClick = { showSortSubmenu = true },
                                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) }
                                    )
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.selection_mode)) },
                                        onClick = { viewModel.toggleSelectionMode(); showMainDropdown = false },
                                        leadingIcon = { Icon(Icons.Default.LibraryAddCheck, contentDescription = null) }
                                    )
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.settings)) },
                                        onClick = { navController.navigate("settings"); showMainDropdown = false },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.back)) },
                                        onClick = { showSortSubmenu = false },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                                    )
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_az)) },
                                        onClick = { viewModel.setSortOrder(SortOrder.NAME); showMainDropdown = false; showSortSubmenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_date)) },
                                        onClick = { viewModel.setSortOrder(SortOrder.DATE); showMainDropdown = false; showSortSubmenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_duration)) },
                                        onClick = { viewModel.setSortOrder(SortOrder.DURATION); showMainDropdown = false; showSortSubmenu = false }
                                    )
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_reverse)) },
                                        onClick = { viewModel.toggleSortReverse(); showMainDropdown = false; showSortSubmenu = false }
                                    )
                                }
                            }
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
                    }
                )

                TabRow(selectedTabIndex = selectedTab) {
                    val tabs = listOf(
                        stringResource(R.string.songs),
                        stringResource(R.string.playlists),
                        stringResource(R.string.artists),
                        stringResource(R.string.albums)
                    )
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { viewModel.setTab(index) }, text = { Text(title) })
                    }
                }
                val searchQuery by viewModel.searchQuery.collectAsState()
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.search) + "...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
            }
        },
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
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val pullRefreshState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        viewModel.scanMusic(force = true)
                        delay(800)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
                        } else {
                            slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "tab_content"
                ) { tab ->
                    when (tab) {
                        0 -> SongsTab(
                            songs, isSelectionMode, selectedSongIds,
                            currentSongId = currentSong?.id,
                            onSelectSong = { viewModel.toggleSongSelection(it.id) },
                            onSongClick = { song -> if (isSelectionMode) viewModel.toggleSongSelection(song.id) else viewModel.playSong(song, songs) },
                            onAddToPlaylist = { showAddToPlaylistDialog = it },
                            onDeleteSong = { showDeleteDialog = it },
                            onPlaySong = { viewModel.playSong(it, songs) }
                        )
                        1 -> PlaylistsTab(
                            playlists,
                            songs,
                            viewModel,
                            onImportClick = { importPlaylistLauncher.launch(arrayOf("application/json", "text/*")) },
                            onCreatePlaylistClick = { showPlaylistCreateDialog = true },
                            onPlaylistClick = { playlist -> navController.navigate("playlist_detail/${playlist.id}") },
                            navController = navController
                        )
                        2 -> ArtistsTab(
                            songs, isSelectionMode, selectedSongIds,
                            currentSongId = currentSong?.id,
                            onSongClick = { song, artistSongs -> if (isSelectionMode) viewModel.toggleSongSelection(song.id) else viewModel.playSong(song, artistSongs) },
                            onLongClick = { song -> if (!isSelectionMode) viewModel.toggleSelectionMode(); viewModel.toggleSongSelection(song.id) },
                            onArtistClick = { artistSongs -> if (isSelectionMode) viewModel.toggleArtistSelection(artistSongs) else viewModel.playArtistSongs(artistSongs) },
                            onPlaySong = { song, artistSongs -> viewModel.playSong(song, artistSongs) },
                            onDeleteSong = { showDeleteDialog = it }
                        )
                        3 -> AlbumsTab(
                            songs, isSelectionMode, selectedSongIds,
                            currentSongId = currentSong?.id,
                            onSongClick = { song, albumSongs -> if (isSelectionMode) viewModel.toggleSongSelection(song.id) else viewModel.playSong(song, albumSongs) },
                            onLongClick = { song -> if (!isSelectionMode) viewModel.toggleSelectionMode(); viewModel.toggleSongSelection(song.id) },
                            onAlbumClick = { albumSongs -> if (isSelectionMode) viewModel.toggleAlbumSelection(albumSongs) else viewModel.playAlbumSongs(albumSongs) },
                            onPlaySong = { song, albumSongs -> viewModel.playSong(song, albumSongs) },
                            onDeleteSong = { showDeleteDialog = it }
                        )
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = showFullPlayer, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()) {
        if (currentSong != null) {
            FullPlayerScreen(viewModel, currentSong!!, isPlaying, navController, onCollapse = { showFullPlayer = false })
        }
    }

    if (showPlaylistCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        val initialSelected = if (selectedSongIds.isNotEmpty()) selectedSongIds.toList() else emptyList()
        AlertDialog(
            onDismissRequest = { showPlaylistCreateDialog = false },
            title = { Text(stringResource(R.string.create_new_playlist)) },
            text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text(stringResource(R.string.playlist_name)) }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { if (playlistName.isNotBlank()) viewModel.createPlaylist(playlistName, initialSelected) { showPlaylistCreateDialog = false; viewModel.clearSelection(); navController.navigate("song_picker/$it") } }) { Text(stringResource(R.string.create)) } },
            dismissButton = { TextButton(onClick = { showPlaylistCreateDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showAddToPlaylistDialog != null) {
        val targetSong = showAddToPlaylistDialog!!
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = null },
            title = { Text(stringResource(R.string.add_to_playlist)) },
            text = {
                LazyColumn {
                    if (playlists.isEmpty()) item { Text(stringResource(R.string.no_playlists), modifier = Modifier.padding(16.dp)) }
                    else items(playlists) { playlist ->
                        TextButton(onClick = { viewModel.addSongToPlaylist(playlist, targetSong.id); showAddToPlaylistDialog = null }, modifier = Modifier.fillMaxWidth()) { Text(playlist.name, style = MaterialTheme.typography.titleMedium) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddToPlaylistDialog = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    // Madde 15: Şarkı silme onay diyaloğu
    if (showDeleteDialog != null) {
        val targetSong = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_song)) },
            text = { Text(stringResource(R.string.delete_song_confirm, targetSong.title)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteSong(targetSong); showDeleteDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// Madde 15: SongsTab artık onDeleteSong ve onPlaySong callback alıyor
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsTab(
    songs: List<Song>,
    isSelectionMode: Boolean,
    selectedSongIds: Set<Long>,
    currentSongId: Long?,
    onSelectSong: (Song) -> Unit,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onPlaySong: (Song) -> Unit
) {
    if (songs.isEmpty()) EmptyStateView(stringResource(R.string.no_music_found), Icons.Outlined.MusicNote)
    else {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp, start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song,
                        selectedSongIds.contains(song.id),
                        isSelectionMode,
                        isPlayingSong = currentSongId == song.id,
                        onClick = { onSongClick(song) },
                        onSelect = { onSelectSong(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onDeleteSong = { onDeleteSong(song) },
                        onPlaySong = { onPlaySong(song) }
                    )
                }
            }
            FastScroller(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun PlaylistsTab(
    playlists: List<Playlist>,
    songs: List<Song>,
    viewModel: MusicViewModel,
    onImportClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.your_playlists), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportClick) { Text(stringResource(R.string.import_label)) }
                Button(onClick = onCreatePlaylistClick) { Text(stringResource(R.string.new_label)) }
            }
        }
        
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyStateView(stringResource(R.string.no_playlists), Icons.Default.PlaylistPlay)
            }
        } else {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(playlists) { playlist ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onPlaylistClick(playlist) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val totalMs = playlist.songIds.mapNotNull { id -> songs.firstOrNull { it.id == id }?.duration ?: 0L }.sum()
                                    Text(
                                        "${stringResource(R.string.songs_count, playlist.songIds.size)} • ${formatDuration(totalMs)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                var showOptions by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showOptions = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = null)
                                    }
                                    DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                                            DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = { showOptions = false; viewModel.deletePlaylist(playlist) }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.export_label)) }, onClick = {
                                                showOptions = false
                                                viewModel.exportPlaylist(playlist) { success, path ->
                                                    if (success && path != null) {
                                                        try {
                                                            val file = File(path)
                                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                                type = "application/json"
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_label)))
                                                        } catch (e: Exception) {
                                                            makeText(context, "${context.getString(R.string.export_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                    } else {
                                                        makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }, leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) })
                                    }
                                }
                            }
                        }
                    }
                }
                FastScroller(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
            }
        }
    }
}

@Composable
fun EmptyStateView(text: String, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PersistentMiniPlayer(currentSong: Song, isPlaying: Boolean, position: Long, duration: Long, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).clickable { onClick() }, color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 8.dp) {
        Column {
            LinearProgressIndicator(
                progress = if (duration > 0) position.toFloat() / duration else 0f,
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SongAlbumArt(song = currentSong, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentSong.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(currentSong.artistName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, contentDescription = null) }
                IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null) }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = null) }
            }
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isPlayingSong: Boolean = false,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDeleteSong: () -> Unit,
    onPlaySong: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    var showDropdown by remember { mutableStateOf(false) }
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isPlayingSong -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { if (isSelectionMode) onSelect() else onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(50.dp)) {
            SongAlbumArt(song = song, modifier = Modifier.fillMaxSize())
            if (isSelectionMode) {
                // Oynatma butonu overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { onPlaySong() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (isSelectionMode) {
            // Madde 11: Seçim kutusu (checkbox) göster
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        } else {
            // Madde 15: Dropdown menü
            Box {
                IconButton(onClick = { showDropdown = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_from_playlist)) },
                            onClick = { showDropdown = false; onRemoveFromPlaylist() },
                            leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to_playlist)) },
                        onClick = { showDropdown = false; onAddToPlaylist() },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_song)) },
                        onClick = { showDropdown = false; onDeleteSong() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

// Madde 8, 9, 16: ArtistsTab yeniden yazıldı - genişletilebilir + seçim + oynatma
@Composable
fun ArtistsTab(
    songs: List<Song>,
    isSelectionMode: Boolean,
    selectedSongIds: Set<Long>,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit,
    onLongClick: (Song) -> Unit,
    onArtistClick: (List<Song>) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onDeleteSong: (Song) -> Unit = {}
) {
    val artistGroups = songs.groupBy { it.artistName }
    // Madde 16: Varsayılan olarak kapalı
    var expandedArtists by remember { mutableStateOf(emptySet<String>()) }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            val sortedArtists = artistGroups.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
            sortedArtists.forEach { artist ->
                val artistSongs = artistGroups[artist] ?: emptyList()
                item(key = "artist_$artist") {
                    val isExpanded = expandedArtists.contains(artist)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                // Madde 8 & 9: Seçim modundaysa toplu seçim, değilse oynat
                                onArtistClick(artistSongs)
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.songs_count, artistSongs.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Madde 16: Genişletme/daraltma ok butonu
                        IconButton(onClick = {
                            expandedArtists = if (isExpanded) expandedArtists - artist else expandedArtists + artist
                        }) {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                    // Madde 16: Sadece genişletilmişse şarkıları göster
                    if (isExpanded) {
                        artistSongs.forEach { song ->
                            SongListItem(
                                song,
                                selectedSongIds.contains(song.id),
                                isSelectionMode,
                                isPlayingSong = currentSongId == song.id,
                                onClick = { onSongClick(song, artistSongs) },
                                onSelect = { onLongClick(song) },
                                onAddToPlaylist = {},
                                onDeleteSong = { onDeleteSong(song) },
                                onPlaySong = { onPlaySong(song, artistSongs) }
                            )
                        }
                    }
                }
            }
        }
        FastScroller(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
    }
}

// Madde 8, 9, 16: AlbumsTab yeniden yazıldı - genişletilebilir + seçim + oynatma
@Composable
fun AlbumsTab(
    songs: List<Song>,
    isSelectionMode: Boolean,
    selectedSongIds: Set<Long>,
    currentSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit,
    onLongClick: (Song) -> Unit,
    onAlbumClick: (List<Song>) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onDeleteSong: (Song) -> Unit = {}
) {
    val albumGroups = songs.groupBy { it.albumName }
    // Madde 16: Varsayılan olarak kapalı
    var expandedAlbums by remember { mutableStateOf(emptySet<String>()) }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            val sortedAlbums = albumGroups.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
            sortedAlbums.forEach { album ->
                val albumSongs = albumGroups[album] ?: emptyList()
                item(key = "album_$album") {
                    val isExpanded = expandedAlbums.contains(album)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                // Madde 8 & 9: Seçim modundaysa toplu seçim, değilse oynat
                                onAlbumClick(albumSongs)
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        val firstSong = albumSongs.firstOrNull()
                        if (firstSong != null) {
                            SongAlbumArt(song = firstSong, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                        } else {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(album, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                            Text(
                                stringResource(R.string.songs_count, albumSongs.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Madde 16: Genişletme/daraltma ok butonu
                        IconButton(onClick = {
                            expandedAlbums = if (isExpanded) expandedAlbums - album else expandedAlbums + album
                        }) {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                    // Madde 16: Sadece genişletilmişse şarkıları göster
                    if (isExpanded) {
                        albumSongs.forEach { song ->
                            SongListItem(
                                song,
                                selectedSongIds.contains(song.id),
                                isSelectionMode,
                                isPlayingSong = currentSongId == song.id,
                                onClick = { onSongClick(song, albumSongs) },
                                onSelect = { onLongClick(song) },
                                onAddToPlaylist = {},
                                onDeleteSong = { onDeleteSong(song) },
                                onPlaySong = { onPlaySong(song, albumSongs) }
                            )
                        }
                    }
                }
            }
        }
        FastScroller(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
    }
}

// Madde 6 & 14: FullPlayerScreen güncellendi
@Composable
fun FullPlayerScreen(viewModel: MusicViewModel, song: Song, isPlaying: Boolean, navController: androidx.navigation.NavController, onCollapse: () -> Unit) {
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffle by viewModel.shuffleMode.collectAsState()
    val repeat by viewModel.repeatMode.collectAsState()
    val nextSong by viewModel.nextSong.collectAsState()
    val lyricsSearchError by viewModel.lyricsSearchError.collectAsState()
    val currentLyrics by viewModel.currentLyrics.collectAsState()

    // Madde 6: Düzenleme diyaloğu
    var showEditDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    LaunchedEffect(currentLyrics) {
        if (currentLyrics.isNotEmpty()) {
            showLyrics = true
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                Text(stringResource(R.string.playing_now), style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_song_info))
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
            if (showLyrics && currentLyrics.isNotEmpty()) {
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    Text(currentLyrics.joinToString("\n") { it.text }, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                SongAlbumArt(song = song, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                if (currentLyrics.isNotEmpty()) {
                    showLyrics = !showLyrics
                } else {
                    viewModel.fetchLyricsForCurrentSong()
                    navController.navigate("lyrics_search_results")
                }
            }) {
                Text(
                    when {
                        currentLyrics.isNotEmpty() && showLyrics -> stringResource(R.string.hide_lyrics)
                        currentLyrics.isNotEmpty() -> stringResource(R.string.show_lyrics)
                        else -> stringResource(R.string.fetch_lyrics)
                    }
                )
            }
            lyricsSearchError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(song.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

            nextSong?.let { next ->
                Text(
                    stringResource(R.string.next_song_label, next.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Slider(value = if (duration > 0) position.toFloat() else 0f, onValueChange = { viewModel.seekTo(it.toLong()) }, valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)), modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position))
                Text(formatDuration(duration))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggleShuffle() }) { Icon(Icons.Default.Shuffle, tint = if (shuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current, contentDescription = null) }
                IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(64.dp)) { Icon(Icons.Default.SkipPrevious, modifier = Modifier.size(48.dp), contentDescription = null) }
                FloatingActionButton(onClick = { viewModel.playPause() }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, modifier = Modifier.size(40.dp), contentDescription = null)
                }
                IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(64.dp)) { Icon(Icons.Default.SkipNext, modifier = Modifier.size(48.dp), contentDescription = null) }
                val repeatIcon = when (repeat) {
                    androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                }
                val repeatTint = when (repeat) {
                    androidx.media3.common.Player.REPEAT_MODE_OFF -> LocalContentColor.current.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.primary
                }
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    Icon(repeatIcon, tint = repeatTint, contentDescription = null)
                }
            }
        }
    }

    // Madde 6: Şarkı bilgisi düzenleme diyaloğu
    if (showEditDialog) {
        var editTitle by remember(song) { mutableStateOf(song.title) }
        var editArtist by remember(song) { mutableStateOf(song.artistName) }
        var editAlbum by remember(song) { mutableStateOf(song.albumName) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit_song_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(stringResource(R.string.song_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text(stringResource(R.string.artist_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editAlbum,
                        onValueChange = { editAlbum = it },
                        label = { Text(stringResource(R.string.album_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateSongMetadata(song, editTitle, editArtist, editAlbum)
                    showEditDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
fun FastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val totalItemsCount = layoutInfo.totalItemsCount
    if (totalItemsCount == 0) return

    val firstVisibleItemIndex = listState.firstVisibleItemIndex
    val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
    
    var scrollerHeight by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val scrollFraction = if (totalItemsCount > 0) {
        val estimatedTotalOffset = totalItemsCount * 100f
        val estimatedCurrentOffset = (firstVisibleItemIndex * 100f) + (firstVisibleItemScrollOffset.toFloat() / 10f)
        (estimatedCurrentOffset / estimatedTotalOffset).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .onGloballyPositioned { coordinates ->
                scrollerHeight = coordinates.size.height.toFloat()
            }
            .pointerInput(totalItemsCount) {
                detectTapGestures { offset ->
                    val y = offset.y
                    val targetFraction = (y / scrollerHeight).coerceIn(0f, 1f)
                    val targetIndex = (targetFraction * totalItemsCount).toInt().coerceAtMost(totalItemsCount - 1)
                    coroutineScope.launch {
                        listState.scrollToItem(targetIndex)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = (scrollFraction * (scrollerHeight - 48f)).coerceAtLeast(0f)
                }
                .padding(vertical = 4.dp)
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        )
    }
}
