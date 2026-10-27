package com.totof.mymusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.totof.mymusic.model.FileNode

@Composable
fun MusicTreeView(
    uiState: MusicUiState,
    selectedPaths: Set<String>,
    onToggleSelection: (FileNode) -> Unit,
    onPlayTrack: (FileNode) -> Unit,
    currentTrackPath: String?,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    fontScale: Float = 1.0f
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Rechercher un morceau, artiste, titre...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Rechercher") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Effacer")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (uiState) {
                is MusicUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is MusicUiState.Success -> {
                    if (searchQuery.isNotBlank()) {
                        val query = searchQuery.trim()
                        val matchingFiles = remember(uiState, query) {
                            uiState.root.getAllFiles().filter { node ->
                                node.name.contains(query, ignoreCase = true) ||
                                node.displayName.contains(query, ignoreCase = true) ||
                                (node.title != null && node.title.contains(query, ignoreCase = true)) ||
                                (node.artist != null && node.artist.contains(query, ignoreCase = true)) ||
                                node.fullPath.contains(query, ignoreCase = true)
                            }
                        }

                        if (matchingFiles.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Aucun morceau trouvé pour \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                items(matchingFiles) { file ->
                                    SearchResultItem(
                                        node = file,
                                        isSelected = file.fullPath in selectedPaths,
                                        onToggleSelection = { onToggleSelection(file) },
                                        onPlayTrack = { onPlayTrack(file) },
                                        isCurrent = file.fullPath == currentTrackPath,
                                        fontScale = fontScale
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            items(uiState.root.children) { child ->
                                FileNodeItem(
                                    node = child,
                                    level = 0,
                                    selectedPaths = selectedPaths,
                                    onToggleSelection = onToggleSelection,
                                    onPlayTrack = onPlayTrack,
                                    currentTrackPath = currentTrackPath,
                                    fontScale = fontScale
                                )
                            }
                        }
                    }
                }
                is MusicUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun FileNodeItem(
    node: FileNode,
    level: Int,
    selectedPaths: Set<String>,
    onToggleSelection: (FileNode) -> Unit,
    onPlayTrack: (FileNode) -> Unit,
    currentTrackPath: String?,
    fontScale: Float = 1.0f
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val allFiles = remember(node) { node.getAllFiles() }
    val isSelected = allFiles.isNotEmpty() && allFiles.all { it.fullPath in selectedPaths }
    val isPartiallySelected = allFiles.isNotEmpty() && !isSelected && allFiles.any { it.fullPath in selectedPaths }
    
    val isCurrent = node.isFile && node.fullPath == currentTrackPath

    val iconSize = (24 * fontScale).dp
    val bodyLargeSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale
    val bodyMediumSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    if (!node.isFile) {
                        isExpanded = !isExpanded 
                    } else {
                        onPlayTrack(node)
                    }
                }
                .padding(vertical = (4 * fontScale).dp, horizontal = (level * 16).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection(node) },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = if (isPartiallySelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.scale(if (fontScale > 1.2f) 1.2f else 1.0f)
            )
            
            if (!node.isFile) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize)
                )
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize).padding(horizontal = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(iconSize))
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(iconSize).padding(horizontal = 4.dp)
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .weight(1f)
            ) {
                Text(
                    text = node.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = bodyLargeSize),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (fontScale > 1.2f) 2 else 1
                )
                
                if (node.isFile && !node.artist.isNullOrBlank() && node.artist != "<unknown>") {
                    Text(
                        text = node.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyMediumSize),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        if (!node.isFile) {
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    node.children.forEach { child ->
                        FileNodeItem(
                            node = child,
                            level = level + 1,
                            selectedPaths = selectedPaths,
                            onToggleSelection = onToggleSelection,
                            onPlayTrack = onPlayTrack,
                            currentTrackPath = currentTrackPath,
                            fontScale = fontScale
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    node: FileNode,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onPlayTrack: () -> Unit,
    isCurrent: Boolean,
    fontScale: Float = 1.0f
) {
    val iconSize = (20 * fontScale).dp
    val bodyLargeSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale
    val bodyMediumSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale
    val labelSmallSize = MaterialTheme.typography.labelSmall.fontSize * fontScale

    val folderName = remember(node.fullPath) {
        val parentPath = node.fullPath.substringBeforeLast("/")
        parentPath.substringAfterLast("/")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = (2 * fontScale).dp)
            .clickable { onPlayTrack() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding((8 * fontScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.scale(if (fontScale > 1.2f) 1.2f else 1.0f)
            )

            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )

            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
            ) {
                Text(
                    text = node.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = bodyLargeSize),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (fontScale > 1.2f) 2 else 1
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!node.artist.isNullOrBlank() && node.artist != "<unknown>") {
                        Text(
                            text = node.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyMediumSize),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (folderName.isNotBlank()) {
                        Text(
                            text = "📁 $folderName",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = labelSmallSize),
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
