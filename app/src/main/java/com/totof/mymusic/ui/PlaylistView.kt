package com.totof.mymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.totof.mymusic.model.FileNode
import com.totof.mymusic.model.Playlist

@Composable
fun PlaylistView(
    playlists: List<Playlist>,
    editingPlaylist: Playlist?,
    onDeletePlaylist: (Playlist) -> Unit,
    onSelectPlaylist: (Playlist?) -> Unit,
    onRemoveTrack: (Playlist, FileNode) -> Unit,
    onAddTracks: () -> Unit,
    onPlayTrack: (FileNode, List<FileNode>) -> Unit,
    onPlayPlaylist: (Playlist, Boolean) -> Unit,
    currentTrackPath: String?,
    modifier: Modifier = Modifier
) {
    if (editingPlaylist != null) {
        PlaylistDetailView(
            playlist = editingPlaylist,
            onBack = { onSelectPlaylist(null) },
            onRemoveTrack = { onRemoveTrack(editingPlaylist, it) },
            onAddTracks = onAddTracks,
            onPlayTrack = { onPlayTrack(it, editingPlaylist.tracks) },
            onPlayPlaylist = { shuffle -> onPlayPlaylist(editingPlaylist, shuffle) },
            currentTrackPath = currentTrackPath,
            modifier = modifier
        )
    } else {
        if (playlists.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Aucune playlist créée", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = modifier.fillMaxSize().padding(8.dp)) {
                items(playlists) { playlist ->
                    PlaylistItem(playlist, onDeletePlaylist, onSelectPlaylist)
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    onBack: () -> Unit,
    onRemoveTrack: (FileNode) -> Unit,
    onAddTracks: () -> Unit,
    onPlayTrack: (FileNode) -> Unit,
    onPlayPlaylist: (Boolean) -> Unit,
    currentTrackPath: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onPlayPlaylist(false) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Lire tout")
            }
            IconButton(onClick = { onPlayPlaylist(true) }) {
                Icon(Icons.Default.Shuffle, contentDescription = "Aléatoire")
            }
            Button(onClick = onAddTracks, modifier = Modifier.padding(start = 8.dp)) {
                Text("Ajouter")
            }
        }
        
        if (playlist.tracks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Cette playlist est vide")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(playlist.tracks) { track ->
                    TrackItem(
                        track = track,
                        onRemove = { onRemoveTrack(track) },
                        onPlay = { onPlayTrack(track) },
                        isCurrent = track.fullPath == currentTrackPath
                    )
                }
            }
        }
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Retour aux playlists")
        }
    }
}

@Composable
fun TrackItem(track: FileNode, onRemove: () -> Unit, onPlay: () -> Unit, isCurrent: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = track.title ?: track.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (!track.artist.isNullOrBlank() && track.artist != "<unknown>") {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Enlever", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onDeletePlaylist: (Playlist) -> Unit,
    onSelectPlaylist: (Playlist) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectPlaylist(playlist) }
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(text = playlist.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${playlist.tracks.size} titre(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDeletePlaylist(playlist) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
