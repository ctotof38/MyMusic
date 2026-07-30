package com.totof.mymusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.totof.mymusic.ui.MusicTab
import com.totof.mymusic.ui.MusicTreeView
import com.totof.mymusic.ui.MusicTreeViewModel
import com.totof.mymusic.ui.PlaylistView
import com.totof.mymusic.ui.PlayerControlBar
import com.totof.mymusic.ui.theme.MyMusicTheme
import com.totof.mymusic.ui.theme.SpringGreen
import com.totof.mymusic.ui.theme.OnSpringGreen

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyMusicTheme {
                val viewModel: MusicTreeViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val currentTab by viewModel.currentTab.collectAsState()
                val selectedPaths by viewModel.selectedPaths.collectAsState()
                val playlists by viewModel.playlists.collectAsState()
                val editingPlaylist by viewModel.editingPlaylist.collectAsState()
                val currentTrack by viewModel.currentTrack.collectAsState()
                val isPlaying by viewModel.isPlaying.collectAsState()
                val playbackPosition by viewModel.playbackPosition.collectAsState()
                val playbackDuration by viewModel.playbackDuration.collectAsState()
                val playbackSpeed by viewModel.playbackSpeed.collectAsState()

                var showPlaylistDialog by remember { mutableStateOf(false) }
                var playlistName by remember { mutableStateOf("") }

                BackHandler(enabled = editingPlaylist != null) {
                    viewModel.selectPlaylistForEditing(null)
                    viewModel.setTab(MusicTab.Playlists)
                }

                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.scanMusic()
                    }
                }

                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.scanMusic()
                    } else {
                        launcher.launch(permission)
                    }
                }

                if (showPlaylistDialog) {
                    AlertDialog(
                        onDismissRequest = { showPlaylistDialog = false },
                        title = { Text("Nouvelle Playlist") },
                        text = {
                            TextField(
                                value = playlistName,
                                onValueChange = { playlistName = it },
                                placeholder = { Text("Nom de la playlist") }
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (playlistName.isNotBlank()) {
                                    viewModel.createPlaylist(playlistName)
                                    playlistName = ""
                                    showPlaylistDialog = false
                                }
                            }) {
                                Text("Créer")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPlaylistDialog = false }) {
                                Text("Annuler")
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(510f / 148f)) {
                            Image(
                                painter = painterResource(id = R.drawable.bandeau),
                                contentDescription = null,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.FillBounds
                            )
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Zone du titre centrée
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "My Music",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                
                                TabRow(
                                    selectedTabIndex = currentTab.ordinal,
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Black,
                                    indicator = { tabPositions ->
                                        TabRowDefaults.SecondaryIndicator(
                                            Modifier.tabIndicatorOffset(tabPositions[currentTab.ordinal]),
                                            color = Color.Black
                                        )
                                    }
                                ) {
                                    Tab(
                                        selected = currentTab == MusicTab.Explorer,
                                        onClick = { viewModel.setTab(MusicTab.Explorer) },
                                        text = { Text("Explorateur") },
                                        icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                                    )
                                    Tab(
                                        selected = currentTab == MusicTab.Playlists,
                                        onClick = { viewModel.setTab(MusicTab.Playlists) },
                                        text = { Text("Playlists") },
                                        icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        PlayerControlBar(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            positionMs = playbackPosition,
                            durationMs = playbackDuration,
                            playbackSpeed = playbackSpeed,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.next() },
                            onPrevious = { viewModel.previous() },
                            onSeek = { viewModel.seekTo(it) },
                            onSpeedChange = { viewModel.setPlaybackSpeed(it) }
                        )
                    },
                    floatingActionButton = {
                        if (currentTab == MusicTab.Explorer && selectedPaths.isNotEmpty()) {
                            if (editingPlaylist == null) {
                                FloatingActionButton(onClick = { showPlaylistDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Créer playlist")
                                }
                            } else {
                                ExtendedFloatingActionButton(
                                    onClick = { 
                                        viewModel.addSelectedTracksToPlaylist()
                                        viewModel.setTab(MusicTab.Playlists)
                                    },
                                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    text = { Text("Ajouter à ${editingPlaylist?.name}") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        if (currentTab == MusicTab.Explorer && editingPlaylist != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mode Ajout : ${editingPlaylist?.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.selectPlaylistForEditing(null) }) {
                                        Text("Annuler")
                                    }
                                }
                            }
                        }

                        when (currentTab) {
                            MusicTab.Explorer -> {
                                MusicTreeView(
                                    uiState = uiState,
                                    selectedPaths = selectedPaths,
                                    onToggleSelection = { viewModel.toggleSelection(it) },
                                    onPlayTrack = { viewModel.playTrack(it) },
                                    currentTrackPath = currentTrack?.fullPath
                                )
                            }
                            MusicTab.Playlists -> {
                                PlaylistView(
                                    playlists = playlists,
                                    editingPlaylist = editingPlaylist,
                                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                    onSelectPlaylist = { viewModel.selectPlaylistForEditing(it) },
                                    onRemoveTrack = { playlist, track -> viewModel.removeTrackFromPlaylist(playlist, track) },
                                    onAddTracks = { viewModel.setTab(MusicTab.Explorer) },
                                    onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                                    onPlayPlaylist = { playlist, shuffle -> viewModel.playPlaylist(playlist, shuffle) },
                                    currentTrackPath = currentTrack?.fullPath
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

