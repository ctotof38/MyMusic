package com.totof.mymusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (uiState) {
            is MusicUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MusicUiState.Success -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(uiState.root.children) { child ->
                        FileNodeItem(
                            node = child,
                            level = 0,
                            selectedPaths = selectedPaths,
                            onToggleSelection = onToggleSelection,
                            onPlayTrack = onPlayTrack,
                            currentTrackPath = currentTrackPath
                        )
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

@Composable
fun FileNodeItem(
    node: FileNode,
    level: Int,
    selectedPaths: Set<String>,
    onToggleSelection: (FileNode) -> Unit,
    onPlayTrack: (FileNode) -> Unit,
    currentTrackPath: String?
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val allFiles = remember(node) { node.getAllFiles() }
    val isSelected = allFiles.isNotEmpty() && allFiles.all { it.fullPath in selectedPaths }
    val isPartiallySelected = allFiles.isNotEmpty() && !isSelected && allFiles.any { it.fullPath in selectedPaths }
    
    val isCurrent = node.isFile && node.fullPath == currentTrackPath

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
                .padding(vertical = 4.dp, horizontal = (level * 16).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection(node) },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = if (isPartiallySelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                )
            )
            
            if (!node.isFile) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp).padding(horizontal = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp).padding(horizontal = 4.dp)
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .weight(1f)
            ) {
                Text(
                    text = node.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                
                if (node.isFile && !node.artist.isNullOrBlank() && node.artist != "<unknown>") {
                    Text(
                        text = node.artist,
                        style = MaterialTheme.typography.bodyMedium,
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
                            currentTrackPath = currentTrackPath
                        )
                    }
                }
            }
        }
    }
}
