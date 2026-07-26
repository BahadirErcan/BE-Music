package com.be.music.ui

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.be.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AlbumArtHelper {
    private val albumArtCache = LruCache<String, ImageBitmap>(100)

    fun getCachedArt(path: String): ImageBitmap? = albumArtCache.get(path)

    suspend fun loadArt(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        getCachedArt(path)?.let { return@withContext it }
        
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                bitmap?.asImageBitmap()?.let {
                    albumArtCache.put(path, it)
                    return@withContext it
                }
            }
        } catch (e: Exception) {
            Log.e("AlbumArtHelper", "Error loading art: $path")
        } finally {
            try { retriever?.release() } catch (ex: Exception) { }
        }
        null
    }
}

@Composable
fun SongAlbumArt(
    song: Song,
    modifier: Modifier = Modifier
) {
    var albumArtBitmap by remember(song.path) { mutableStateOf(AlbumArtHelper.getCachedArt(song.path)) }

    if (albumArtBitmap == null) {
        LaunchedEffect(song.path) {
            albumArtBitmap = AlbumArtHelper.loadArt(song.path)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        albumArtBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}
