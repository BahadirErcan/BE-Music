package com.be.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.be.music.R
import com.be.music.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerScreen(
    viewModel: MusicViewModel,
    playlistId: Int,
    onBack: () -> Unit
) {
    val songs by viewModel.songsState.collectAsState()
    val playlists by viewModel.playlistsState.collectAsState()
    val playlist = remember(playlists, playlistId) { playlists.find { it.id == playlistId } }
    
    // Initial selection from playlist
    var selectedIds by remember(playlist) { 
        mutableStateOf(playlist?.songIds?.toSet() ?: emptySet<Long>()) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_song_dialog)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        playlist?.let {
                            viewModel.updatePlaylistSongs(it, selectedIds.toList())
                        }
                        onBack()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.done))
                    }
                }
            )
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Kütüphanede şarkı yok.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    val isSelected = selectedIds.contains(song.id)
                    SongPickerItem(
                        song = song,
                        isSelected = isSelected,
                        onToggle = {
                            selectedIds = if (isSelected) {
                                selectedIds - song.id
                            } else {
                                selectedIds + song.id
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SongPickerItem(
    song: Song,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Check, contentDescription = null) // Placeholder for SongAlbumArt if not available
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(song.artistName, style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        }
    }
}
