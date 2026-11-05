package com.troikoss.continuum_explorer.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * Universal Media Viewer for Images, Audio, and Video files.
 */
@Composable
fun MediaViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = file.extension.lowercase()
    val isImage = ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    val isAudio = ext in listOf("mp3", "wav", "ogg", "flac", "m4a")
    val isVideo = ext in listOf("mp4", "mkv", "avi", "webm", "3gp")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icon = when {
                        isImage -> Icons.Default.Image
                        isAudio -> Icons.Default.MusicNote
                        else -> Icons.Default.Movie
                    }
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(file.name, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Text("${file.length() / 1024} KB", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isImage) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isAudio) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                    Text(file.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Audio Track Preview", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                    Text(file.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Video Clip Preview", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}
