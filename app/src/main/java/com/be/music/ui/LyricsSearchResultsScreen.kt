package com.be.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.be.music.R
import com.be.music.youtube.YoutubeVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSearchResultsScreen(
    onBack: () -> Unit,
    onVideoSelected: (videoId: String) -> Unit,
    viewModel: MusicViewModel
) {
    val lyricsSearchResults by viewModel.lyricsSearchResults.collectAsStateWithLifecycle()
    val lyricsSearchLoading by viewModel.lyricsSearchLoading.collectAsStateWithLifecycle()
    val lyricsSearchError by viewModel.lyricsSearchError.collectAsStateWithLifecycle()
    
    val checkingSubtitles by viewModel.checkingSubtitles.collectAsStateWithLifecycle()
    val availableSubtitles by viewModel.availableSubtitles.collectAsStateWithLifecycle()
    val subtitlesError by viewModel.subtitlesError.collectAsStateWithLifecycle()

    var selectedVideoId by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.select_video_for_lyrics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                    .padding(innerPadding)
        ) {
            if (lyricsSearchLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (lyricsSearchError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = lyricsSearchError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(onClick = onBack) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }
            } else if (lyricsSearchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(R.string.no_results_found))
                        Button(onClick = onBack) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(lyricsSearchResults) { video ->
                        SearchResultItem(
                            video = video,
                            isDownloading = isDownloading,
                            onSelect = {
                                selectedVideoId = video.id
                                viewModel.checkSubtitlesForVideo(video.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (selectedVideoId != null) {
        AlertDialog(
            onDismissRequest = { 
                if (!isDownloading && !checkingSubtitles) {
                    selectedVideoId = null 
                }
            },
            title = { 
                Text(
                    if (availableSubtitles.isNotEmpty() && availableSubtitles.first().isAutomatic) {
                        stringResource(R.string.select_auto_subtitle_lang)
                    } else if (availableSubtitles.isNotEmpty()) {
                        stringResource(R.string.select_manual_subtitle)
                    } else {
                        stringResource(R.string.select_subtitle)
                    }
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (checkingSubtitles || isDownloading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (isDownloading) stringResource(R.string.downloading_subtitles) else stringResource(R.string.checking_subtitles),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (subtitlesError != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                subtitlesError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (availableSubtitles.isEmpty()) {
                        Text(stringResource(R.string.no_subtitles_available), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableSubtitles) { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            isDownloading = true
                                            viewModel.downloadSelectedSubtitle(selectedVideoId!!, option) { success ->
                                                isDownloading = false
                                                if (success) {
                                                    val videoId = selectedVideoId
                                                    selectedVideoId = null
                                                    onVideoSelected(videoId ?: "")
                                                }
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(option.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(option.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                if (option.isAutomatic) stringResource(R.string.subtitle_type_auto) else stringResource(R.string.subtitle_type_manual),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (option.isAutomatic)
                                                MaterialTheme.colorScheme.surfaceVariant
                                            else
                                                MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = if (option.isAutomatic)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (isDownloading || checkingSubtitles) {
                    TextButton(onClick = {
                        viewModel.cancelLyricsDownload()
                        isDownloading = false
                        selectedVideoId = null
                    }) {
                        Text(stringResource(R.string.cancel_download))
                    }
                } else {
                    TextButton(onClick = { selectedVideoId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun SearchResultItem(
    video: YoutubeVideo,
    isDownloading: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isDownloading) { onSelect() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.durationSeconds > 0) {
                        Text(
                            text = "• ${formatDurationSeconds(video.durationSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
