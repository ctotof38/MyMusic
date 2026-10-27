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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _stopAfterCurrentTrack = MutableStateFlow(false)
    val stopAfterCurrentTrack: StateFlow<Boolean> = _stopAfterCurrentTrack.asStateFlow()

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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
                        tracks = entity.tracks
                            .sortedBy { it.position }
                            .map { track ->
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
                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
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

    fun setStopAfterCurrentTrack(enabled: Boolean) {
        _stopAfterCurrentTrack.value = enabled
        if (enabled) {
            applyStopAfterCurrentTrackToActiveQueue()
        }
    }

    fun toggleStopAfterCurrentTrack() {
        val newMode = !_stopAfterCurrentTrack.value
        _stopAfterCurrentTrack.value = newMode
        if (newMode) {
            applyStopAfterCurrentTrackToActiveQueue()
        }
    }

    private fun applyStopAfterCurrentTrackToActiveQueue() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount > 1) {
            val currentIndex = controller.currentMediaItemIndex
            val totalItems = controller.mediaItemCount
            if (currentIndex + 1 < totalItems) {
                controller.removeMediaItems(currentIndex + 1, totalItems)
            }
            if (currentIndex > 0) {
                controller.removeMediaItems(0, currentIndex)
            }
        }
    }

    fun setFontScale(scale: Float) {
        _fontScale.value = scale.coerceIn(1.0f, MAX_ZOOM_SCALE)
    }

    fun toggleZoom() {
        _fontScale.value = if (_fontScale.value > 1.2f) 1.0f else MAX_ZOOM_SCALE
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    companion object {
        const val MAX_ZOOM_SCALE = 1.6f
    }

    private var pendingUri: android.net.Uri? = null

    fun playUri(uri: android.net.Uri) {
        pendingUri = uri
        val controller = mediaController
        if (controller == null) {
            mediaControllerFuture?.addListener({
                val asyncController = mediaController
                val uriToPlay = pendingUri
                if (asyncController != null && uriToPlay != null) {
                    executePlayUri(asyncController, uriToPlay)
                    pendingUri = null
                }
            }, MoreExecutors.directExecutor())
        } else {
            executePlayUri(controller, uri)
            pendingUri = null
        }
    }

    private fun executePlayUri(controller: MediaController, uri: android.net.Uri) {
        controller.shuffleModeEnabled = false
        val mediaItem = MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .build()

        controller.setMediaItems(listOf(mediaItem))
        controller.prepare()
        controller.play()

        val fileName = uri.lastPathSegment ?: "Fichier audio"
        _currentTrack.value = FileNode(
            name = fileName,
            isFile = true,
            fullPath = uri.toString(),
            title = fileName
        )
    }

    fun playTrack(track: FileNode, playlist: List<FileNode>? = null) {
        val controller = mediaController ?: return
        
        controller.shuffleModeEnabled = false // Disable shuffle when playing a specific track
        
        val effectivePlaylist = if (_stopAfterCurrentTrack.value) {
            listOf(track)
        } else {
            playlist ?: track.parent?.children?.filter { it.isFile } ?: listOf(track)
        }

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

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _repeatMode.value = nextMode
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
                    val currentTracksSize = playlist.tracks.size
                    val trackEntities = newTracks.mapIndexed { index, it ->
                        TrackEntity(
                            playlistId = entity.id,
                            name = it.name,
                            fullPath = it.fullPath,
                            title = it.title,
                            artist = it.artist,
                            position = currentTracksSize + index
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
                    val trackEntities = tracks.mapIndexed { index, it ->
                        TrackEntity(
                            playlistId = playlistId,
                            name = it.name,
                            fullPath = it.fullPath,
                            title = it.title,
                            artist = it.artist,
                            position = index
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

    fun exportPlaylistsToUri(uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonArray = org.json.JSONArray()
                val playlistsList = _playlists.value
                playlistsList.forEach { playlist ->
                    val playlistObj = org.json.JSONObject()
                    playlistObj.put("name", playlist.name)
                    val tracksArray = org.json.JSONArray()
                    playlist.tracks.forEach { track ->
                        val trackObj = org.json.JSONObject()
                        trackObj.put("name", track.name)
                        trackObj.put("fullPath", track.fullPath)
                        trackObj.put("title", track.title ?: "")
                        trackObj.put("artist", track.artist ?: "")
                        tracksArray.put(trackObj)
                    }
                    playlistObj.put("tracks", tracksArray)
                    jsonArray.put(playlistObj)
                }

                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
                }
                
                launch(Dispatchers.Main) {
                    onResult(true, "${playlistsList.size} playlist(s) exportée(s)")
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    onResult(false, e.message ?: "Erreur lors de l'exportation")
                }
            }
        }
    }

    fun importPlaylistsFromUri(uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalStateException("Impossible de lire le fichier")

                val jsonArray = org.json.JSONArray(jsonString)

                // Scan device files to resolve missing paths on reinstall or file move
                val scanner = MusicScanner(getApplication())
                val rootNode = scanner.scanForMp3s()
                val allDeviceFiles = rootNode.getAllFiles()

                var importedCount = 0

                for (i in 0 until jsonArray.length()) {
                    val playlistObj = jsonArray.getJSONObject(i)
                    val playlistName = playlistObj.optString("name", "Playlist Importée")
                    val tracksArray = playlistObj.optJSONArray("tracks") ?: org.json.JSONArray()

                    val existingPlaylist = musicDao.getPlaylistByName(playlistName)
                    val playlistId = if (existingPlaylist != null) {
                        existingPlaylist.id
                    } else {
                        musicDao.insertPlaylist(PlaylistEntity(name = playlistName))
                    }

                    val currentTracks = musicDao.getTracksForPlaylistSync(playlistId)
                    var currentPosition = currentTracks.size

                    val trackEntitiesToInsert = mutableListOf<TrackEntity>()

                    for (j in 0 until tracksArray.length()) {
                        val trackObj = tracksArray.getJSONObject(j)
                        val originalPath = trackObj.optString("fullPath", "")
                        val fileName = trackObj.optString("name", "")
                        val title = trackObj.optString("title", "").ifBlank { null }
                        val artist = trackObj.optString("artist", "").ifBlank { null }

                        var resolvedPath = originalPath
                        var resolvedName = fileName

                        // If originalPath does not exist on this device, search scanned device files
                        if (originalPath.isBlank() || !java.io.File(originalPath).exists()) {
                            val match = allDeviceFiles.find { deviceFile ->
                                deviceFile.name.equals(fileName, ignoreCase = true) ||
                                (title != null && deviceFile.title.equals(title, ignoreCase = true))
                            }
                            if (match != null) {
                                resolvedPath = match.fullPath
                                resolvedName = match.name
                            }
                        }

                        val alreadyInPlaylist = currentTracks.any { it.fullPath == resolvedPath } ||
                                                trackEntitiesToInsert.any { it.fullPath == resolvedPath }

                        if (resolvedPath.isNotBlank() && !alreadyInPlaylist) {
                            trackEntitiesToInsert.add(
                                TrackEntity(
                                    playlistId = playlistId,
                                    name = if (resolvedName.isNotBlank()) resolvedName else "Piste",
                                    fullPath = resolvedPath,
                                    title = title,
                                    artist = artist,
                                    position = currentPosition++
                                )
                            )
                        }
                    }

                    if (trackEntitiesToInsert.isNotEmpty()) {
                        musicDao.insertTracks(trackEntitiesToInsert)
                    }
                    importedCount++
                }

                launch(Dispatchers.Main) {
                    onResult(true, "$importedCount playlist(s) importée(s)")
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    onResult(false, e.message ?: "Erreur lors de l'importation")
                }
            }
        }
    }

    fun moveTrack(playlist: Playlist, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val playlistEntity = musicDao.getPlaylistByName(playlist.name) ?: return@launch
            val currentEntities = musicDao.getTracksForPlaylistSync(playlistEntity.id)
            
            if (currentEntities.size == playlist.tracks.size) {
                val reordered = currentEntities.toMutableList()
                val entityToMove = reordered.removeAt(fromIndex)
                reordered.add(toIndex, entityToMove)
                
                val entitiesToUpdate = reordered.mapIndexed { index, entity ->
                    entity.copy(position = index)
                }
                musicDao.updateTracks(entitiesToUpdate)
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
