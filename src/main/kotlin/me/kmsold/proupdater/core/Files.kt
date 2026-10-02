package me.kmsold.proupdater.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

/**
 * Read access to the game folder. Paths are relative to the game directory and always use `/`,
 * the way they appear in the pack manifest.
 */
interface FileSource {
    fun read(path: String): ByteArray?
    fun exists(path: String): Boolean
}

/** The real game folder. */
class DirectorySource(val root: Path) : FileSource {

    fun resolve(path: String): Path {
        require(GamePaths.isSafe(path)) { "Refusing to touch '$path' outside the game folder" }
        return root.resolve(path)
    }

    override fun read(path: String): ByteArray? {
        if (!GamePaths.isSafe(path)) return null
        val file = resolve(path)
        return if (file.isRegularFile()) file.readBytes() else null
    }

    override fun exists(path: String): Boolean = GamePaths.isSafe(path) && resolve(path).exists()

    /** Writes [changes] to disk; a null value deletes the file. Each file is replaced atomically. */
    fun commit(changes: Map<String, ByteArray?>) {
        for ((path, bytes) in changes) {
            val file = resolve(path)
            if (bytes == null) {
                file.deleteIfExists()
                continue
            }
            file.parent?.createDirectories()
            val tmp = file.resolveSibling(file.fileName.toString() + ".proupdater-tmp")
            Files.write(tmp, bytes)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

/** An in-memory folder, for the tests. */
class MapSource(val files: MutableMap<String, ByteArray> = mutableMapOf()) : FileSource {
    override fun read(path: String): ByteArray? = files[path]
    override fun exists(path: String): Boolean = path in files || files.keys.any { it.startsWith("$path/") }
}

/**
 * Everything one run wants to write, kept in memory until the backup of the old contents is made.
 * Reads see the pending writes, so steps can be chained on the same file.
 */
class FileChanges(private val base: FileSource) {

    private val pending = LinkedHashMap<String, ByteArray?>()

    fun read(path: String): ByteArray? = if (path in pending) pending[path] else base.read(path)

    fun readText(path: String): String? = read(path)?.toString(Charsets.UTF_8)

    fun exists(path: String): Boolean = if (path in pending) pending[path] != null else base.exists(path)

    fun write(path: String, bytes: ByteArray) {
        pending[path] = bytes
    }

    fun writeText(path: String, text: String) = write(path, text.toByteArray(Charsets.UTF_8))

    fun delete(path: String) {
        pending[path] = null
    }

    /** Only the files that really end up different from what is on disk now. */
    fun changed(): Map<String, ByteArray?> = pending.filter { (path, bytes) ->
        val old = base.read(path)
        if (bytes == null) old != null || base.exists(path) else old == null || !old.contentEquals(bytes)
    }

    /** The current contents of every file in [changed] that already exists and would be lost. */
    fun originals(): Map<String, ByteArray> =
        changed().keys.mapNotNull { path -> base.read(path)?.let { path to it } }.toMap()

    /** Files that [changed] would create from nothing. */
    fun created(): List<String> = changed().filter { (path, bytes) -> bytes != null && base.read(path) == null }.keys.toList()
}

object GamePaths {
    const val DIR = "proupdater"
    const val DEFAULTS = "$DIR/defaults"
    const val MANIFEST = "$DEFAULTS/manifest.json"
    const val RESOURCE_PACKS = "$DEFAULTS/resourcepacks.json"
    const val STATE = "$DIR/state.json"
    const val PENDING = "$DIR/pending.json"
    const val BACKUPS = "$DIR/backups"
    const val OPTIONS = "options.txt"

    /** Files in `defaults/` that describe the pack rather than being part of it. */
    val META_FILES = setOf("manifest.json", "resourcepacks.json")

    fun defaultsPath(path: String) = "$DEFAULTS/$path"

    /** Relative, no `..`, no backslashes: a manifest or a backup can never reach outside the game folder. */
    fun isSafe(path: String): Boolean =
        path.isNotEmpty() && !path.startsWith("/") && '\\' !in path && ':' !in path &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
