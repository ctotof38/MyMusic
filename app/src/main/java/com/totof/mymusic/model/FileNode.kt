package com.totof.mymusic.model

data class FileNode(
    val name: String,
    val isFile: Boolean,
    val fullPath: String,
    val title: String? = null,
    val artist: String? = null,
    val children: MutableList<FileNode> = mutableListOf()
) {
    val displayName: String
        get() {
            if (!isFile) return name
            val isGenericTitle = title?.trim()?.lowercase()?.matches(Regex("track\\s*\\d+")) ?: true
            return if (isGenericTitle || title.isNullOrBlank()) {
                name.substringBeforeLast(".")
            } else {
                title
            }
        }

    // Helper to find or create a child directory
    fun getOrCreateDirectory(name: String, path: String): FileNode {
        return children.find { it.name == name && !it.isFile }
            ?: FileNode(name, false, path).also { children.add(it) }
    }

    // Sort children: directories first, then files, both alphabetically
    fun sort() {
        children.sortWith(compareBy({ it.isFile }, { it.name.lowercase() }))
        children.forEach { it.sort() }
    }

    /**
     * Returns all MP3 files contained in this node and its subdirectories.
     */
    fun getAllFiles(): List<FileNode> {
        val files = mutableListOf<FileNode>()
        if (isFile) {
            files.add(this)
        } else {
            children.forEach { files.addAll(it.getAllFiles()) }
        }
        return files
    }
}
