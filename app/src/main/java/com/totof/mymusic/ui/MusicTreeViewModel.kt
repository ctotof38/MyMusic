package com.totof.mymusic.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.totof.mymusic.model.FileNode
import com.totof.mymusic.model.Playlist
import com.totof.mymusic.data.*
import com.totof.mymusic.player.PlaybackService
import com.totof.mymusic.scanner.MusicScanner
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicTreeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MusicDatabase.getDatabase(application)
    private val musicDao = database.musicDao()

    private val _uiState = MutableStateFlow<MusicUiState>(MusicUiState.Loading)
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val _currentTab = MutableStateFlow(MusicTab.Explorer)
    val currentTab: StateFlow<MusicTab> = _currentTab.asStateFlow()

    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _editingPlaylist = MutableStateFlow<Playlist?>(null)
    val editingPlaylist: StateFlow<Playlist?> = _editingPlaylist.asStateFlow()

    // Playback state
    private val _currentTrack = MutableStateFlow<FileNode?>(null)
    val currentTrack: StateFlow<FileNode?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(value = false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mediaController: MediaController?
        get() = if (mediaControllerFuture?.isDone == true) mediaControllerFuture?.get() else null

    init {
        initializeController()
        observePlaylists()
        startPlaybackProgressUpdate()
    }

    private fun startPlaybackProgressUpdate() {
        viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _playbackPosition.value = controller.currentPosition
                        _playbackDuration.value = controller.duration.coerceAtLeast(0L)
                    }
                }
                delay(500)
            }
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            musicDao.getPlaylistsWithTracks().collectLatest { entities ->
                val playlistModels = entities.map { entity ->
                    Playlist(
                        name = entity.playlist.name,
                        tracks = entity.tracks.map { track ->
                            FileNode(
                                name = track.name,
                                isFile = true,
                                fullPath = track.fullPath,
                                title = track.title,
                                artist = track.artist,
                            )
                        }
                    )
                }
                _playlists.value = playlistModels
                // Update editing playlist if it's currently open
                _editingPlaylist.value?.let { current ->
                    _editingPlaylist.value = playlistModels.find { it.name == current.name }
                }
            }
        }
    }

    private fun initializeController() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            updatePlaybackState()
            mediaController?.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateCurrentTrackFromMediaItem(mediaItem)
                    }
                    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                        _playbackSpeed.value = playbackParameters.speed
                    }
                }
            )
        }, MoreExecutors.directExecutor())
    }

    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        updateCurrentTrackFromMediaItem(controller.currentMediaItem)
    }

    private fun updateCurrentTrackFromMediaItem(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            _currentTrack.value = null
            return
        }
        val path = mediaItem.mediaId
        // Find the node in the tree if possible, or just create a dummy one for UI
        // For now, let's assume we can find it if we search our success state
        val state = _uiState.value
        if (state is MusicUiState.Success) {
            _currentTrack.value = state.root.getAllFiles().find { it.fullPath == path }
        }
    }

    fun playTrack(track: FileNode, playlist: List<FileNode>? = null) {
        val controller = mediaController ?: return
        
        controller.shuffleModeEnabled = false // Disable shuffle when playing a specific track
        
        val effectivePlaylist = playlist ?: track.parent?.children?.filter { it.isFile } ?: listOf(track)

        val mediaItems = effectivePlaylist.map {
            MediaItem.Builder()
                .setMediaId(it.fullPath)
                .setUri(it.fullPath)
                .build()
        }
        
        val startIndex = mediaItems.indexOfFirst { it.mediaId == track.fullPath }
        
        controller.setMediaItems(mediaItems, startIndex.coerceAtLeast(0), 0L)
        controller.prepare()
        controller.play()
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean) {
        val controller = mediaController ?: return
        if (playlist.tracks.isEmpty()) return

        val mediaItems = playlist.tracks.map {
            MediaItem.Builder()
                .setMediaId(it.fullPath)
                .setUri(it.fullPath)
                .build()
        }

        controller.shuffleModeEnabled = shuffle
        controller.setMediaItems(mediaItems)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun next() {
        mediaController?.seekToNext()
    }

    fun previous() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackPosition.value = positionMs
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    override fun onCleared() {
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        super.onCleared()
    }

    fun setTab(tab: MusicTab) {
        _currentTab.value = tab
    }

    fun selectPlaylistForEditing(playlist: Playlist?) {
        _editingPlaylist.value = playlist
    }

    fun removeTrackFromPlaylist(playlist: Playlist, track: FileNode) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = musicDao.getPlaylistByName(playlist.name)
            if (entity != null) {
                musicDao.deleteTrackFromPlaylist(entity.id, track.fullPath)
            }
        }
    }

    fun addSelectedTracksToPlaylist() {
        val playlist = _editingPlaylist.value ?: return
        val state = _uiState.value
        if (state !is MusicUiState.Success) return

        val selected = _selectedPaths.value
        val newTracks = state.root.getAllFiles().filter { it.fullPath in selected }

        if (newTracks.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val entity = musicDao.getPlaylistByName(playlist.name)
                if (entity != null) {
                    val trackEntities = newTracks.map {
                        TrackEntity(
                            playlistId = entity.id,
                            name = it.name,
                            fullPath = it.fullPath,
                            title = it.title,
                            artist = it.artist
                        )
                    }
                    musicDao.insertTracks(trackEntities)
                    _selectedPaths.value = emptySet()
                }
            }
        }
    }

    fun toggleSelection(node: FileNode) {
        val allTracks = node.getAllFiles()
        val allPaths = allTracks.map { it.fullPath }
        
        val currentSelected = _selectedPaths.value.toMutableSet()
        if (allPaths.all { it in currentSelected }) {
            // All are selected, unselect all
            currentSelected.removeAll(allPaths.toSet())
        } else {
            // Not all are selected, select all
            currentSelected.addAll(allPaths)
        }
        _selectedPaths.value = currentSelected
    }

    fun createPlaylist(name: String) {
        val state = _uiState.value
        if (state is MusicUiState.Success) {
            val selected = _selectedPaths.value
            val tracks = state.root.getAllFiles().filter { it.fullPath in selected }
            
            if (tracks.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    val playlistId = musicDao.insertPlaylist(PlaylistEntity(name = name))
                    val trackEntities = tracks.map {
                        TrackEntity(
                            playlistId = playlistId,
                            name = it.name,
                            fullPath = it.fullPath,
                            title = it.title,
                            artist = it.artist
                        )
                    }
                    musicDao.insertTracks(trackEntities)
                    _selectedPaths.value = emptySet()
                }
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = musicDao.getPlaylistByName(playlist.name)
            if (entity != null) {
                musicDao.deletePlaylist(entity)
            }
        }
    }

    fun scanMusic() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = MusicUiState.Loading
            try {
                val scanner = MusicScanner(getApplication())
                val root = scanner.scanForMp3s()
                _uiState.value = MusicUiState.Success(root)
            } catch (e: Exception) {
                _uiState.value = MusicUiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}

sealed class MusicUiState {
    object Loading : MusicUiState()
    data class Success(val root: FileNode) : MusicUiState()
    data class Error(val message: String) : MusicUiState()
}

enum class MusicTab {
    Explorer, Playlists
}
