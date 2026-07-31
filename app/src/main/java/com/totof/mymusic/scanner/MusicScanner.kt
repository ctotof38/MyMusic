package com.totof.mymusic.scanner

import android.content.Context
import android.provider.MediaStore
import com.totof.mymusic.model.FileNode
import java.io.File

class MusicScanner(private val context: Context) {

    fun scanForMp3s(): FileNode {
        val root = FileNode("Ma Musique", false, "")
        val mp3DataList = mutableListOf<Mp3Metadata>()

        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("audio/mpeg") // mp3

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                
                if (path != null) {
                    mp3DataList.add(Mp3Metadata(path, title, artist))
                }
            }
        }

        buildTree(root, mp3DataList)
        root.sort()
        return root
    }

    private fun buildTree(root: FileNode, mp3s: List<Mp3Metadata>) {
        for (mp3 in mp3s) {
            val file = File(mp3.path)
            val segments = getRelevantSegments(file)
            
            var currentNode = root
            var currentPathBuilder = ""
            
            for (i in 0 until (segments.size - 1)) {
                val segment = segments[i]
                currentPathBuilder += "/$segment"
                currentNode = currentNode.getOrCreateDirectory(segment, currentPathBuilder)
            }
            
            val fileName = segments.last()
            val newNode = FileNode(
                name = fileName,
                isFile = true,
                fullPath = mp3.path,
                title = mp3.title,
                artist = mp3.artist
            )
            newNode.parent = currentNode
            currentNode.children.add(newNode)
        }
    }

    private data class Mp3Metadata(
        val path: String,
        val title: String?,
        val artist: String?
    )

    /**
     * Extracts segments from the path, ignoring common storage roots like /storage/emulated/0
     */
    private fun getRelevantSegments(file: File): List<String> {
        val absolutePath = file.absolutePath
        // On Android, typical storage paths start with /storage/emulated/0 or /storage/XXXX-XXXX
        // We want to skip the technical storage part.
        
        val parts = absolutePath.split(File.separator).filter { it.isNotEmpty() }
        
        // Find where the "real" user folders start. 
        // Usually after /storage/emulated/0 or /storage/ABCD-1234
        if (parts.size >= 3 && parts[0] == "storage") {
            return if (parts[1] == "emulated" && parts.size >= 4) {
                parts.drop(3) // Skip storage/emulated/0
            } else {
                parts.drop(2) // Skip storage/XXXX-XXXX
            }
        }
        
        return parts
    }
}
