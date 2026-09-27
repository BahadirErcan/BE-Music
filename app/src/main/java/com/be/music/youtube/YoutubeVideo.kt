package com.be.music.youtube

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.be.music.ui.formatDurationSeconds

data class YoutubeVideo(
    val id: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    /** Saniye cinsinden süre. Canlı yayınlarda negatif olabilir. */
    val durationSeconds: Long
)


@Composable
fun YoutubeVideoItem(
    video: YoutubeVideo,
    onDownloadMusic: () -> Unit,
    onDownloadVideo: () -> Unit,
    onCancelMusic: () -> Unit = {},
    onCancelVideo: () -> Unit = {},
    isMusicDownloading: Boolean = false,
    isVideoDownloading: Boolean = false,
    musicProgress: Int = 0,
    videoProgress: Int = 0
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(80.dp)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=${video.id}"))
                            context.startActivity(intent)
                        },
                    contentScale = ContentScale.Crop
                )
                if (video.durationSeconds > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = formatDurationSeconds(video.durationSeconds),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = video.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(text = video.author, style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (isMusicDownloading) onCancelMusic() else onDownloadMusic() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMusicDownloading) Color(0xFF1565C0) else Color(0xFF1976D2),
                            disabledContainerColor = Color(0xFF1565C0)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (isMusicDownloading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${musicProgress}%", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            }
                        } else {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Button(
                        onClick = { if (isVideoDownloading) onCancelVideo() else onDownloadVideo() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVideoDownloading) Color(0xFF6A1B9A) else Color(0xFF7B1FA2),
                            disabledContainerColor = Color(0xFF6A1B9A)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (isVideoDownloading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${videoProgress}%", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            }
                        } else {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
