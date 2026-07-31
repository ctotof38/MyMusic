package com.totof.mymusic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.totof.mymusic.model.FileNode
import kotlin.math.log2
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControlBar(
    currentTrack: FileNode?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (currentTrack == null) return

    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = currentTrack.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!currentTrack.artist.isNullOrBlank() && (currentTrack.artist != "<unknown>")) {
                        Text(
                            text = currentTrack.artist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Précédent")
                }

                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Lire"
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Suivant")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(positionMs),
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { onSeek((it * durationMs).toLong()) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    thumb = {} // On enlève le bouton curseur vertical
                )
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Contrôle de la vitesse
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vitesse: ${"%.2f".format(playbackSpeed)}x",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(90.dp)
                )
                Slider(
                    value = log2(playbackSpeed.toDouble()).toFloat(),
                    onValueChange = { 
                        onSpeedChange(2.0.pow(it.toDouble()).toFloat())
                    },
                    valueRange = -1f..1f,
                    modifier = Modifier.weight(1f),
                    thumb = {} // On enlève aussi le curseur vertical pour la vitesse
                )
                TextButton(onClick = { onSpeedChange(1.0f) }) {
                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
