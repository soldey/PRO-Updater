package me.kmsold.proupdater.core

import me.kmsold.proupdater.steps.Steps
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * A backup is a zip with the old contents under `files/` and `backup.json` listing the files the
 * run created from nothing, so restoring can remove them again.
 */
object Backups {

    private const val INFO = "backup.json"
    private const val FILES = "files/"

    /** Older backups are removed once there are more than this many. */
    const val KEEP = 15

    class Info {
        var createdAt: String = ""
        var reason: String = ""
        var created: List<String> = emptyList()
    }

    class Contents(val info: Info, val files: Map<String, ByteArray>)

    fun zip(originals: Map<String, ByteArray>, created: List<String>, reason: String, createdAt: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val info = Info().also {
                it.createdAt = createdAt
                it.reason = reason
                it.created = created.sorted()
            }
            zip.putNextEntry(ZipEntry(INFO))
            zip.write(ModelJson.write(info).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((path, bytes) in originals.toSortedMap()) {
                zip.putNextEntry(ZipEntry(FILES + path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun unzip(bytes: ByteArray): Contents? = runCatching {
        var info: Info? = null
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = zip.readBytes()
                when {
                    entry.name == INFO -> info = ModelJson.gson.fromJson(content.toString(Charsets.UTF_8), Info::class.java)
                    entry.name.startsWith(FILES) && !entry.isDirectory -> {
                        val path = entry.name.removePrefix(FILES)
                        if (GamePaths.isSafe(path)) files[path] = content
                    }
                }
            }
        }
        val parsed = info ?: return@runCatching null
        @Suppress("SENSELESS_COMPARISON")
        if (parsed.created == null) parsed.created = emptyList()
        Contents(parsed, files)
    }.getOrNull()

    /**
     * Puts the backed-up files back and removes the ones the backed-up run created; with [only],
     * just those paths, so one mod's settings can come back while everything else stays.
     */
    fun restore(contents: Contents, files: FileChanges, only: Set<String>? = null) {
        for ((path, bytes) in contents.files) {
            if (only == null || path in only) files.write(path, bytes)
        }
        for (path in contents.info.created) {
            if (GamePaths.isSafe(path) && path !in contents.files && (only == null || path in only)) files.delete(path)
        }
    }

    /** Every path a backup can change: the saved files and the ones it would remove. */
    fun paths(contents: Contents): List<String> =
        (contents.files.keys + contents.info.created.filter(GamePaths::isSafe)).distinct().sorted()

    /** What "Make a backup" saves: every file PRO-Updater may change. Never all of `config/`, which is mostly caches. */
    fun managedPaths(manifest: Manifest?, state: InstallState?): List<String> =
        (listOf(GamePaths.OPTIONS) + manifest?.payload()?.keys.orEmpty() + state?.files?.keys.orEmpty() + Steps.all.flatMap { it.files })
            .filter(GamePaths::isSafe)
            .distinct()
            .sorted()

    /** Zips the [managedPaths] that exist now. Returns the backup name, or null when there was nothing to save. */
    fun makeManual(gameDir: Path, manifest: Manifest?, state: InstallState?, now: ZonedDateTime): String? {
        val disk = DirectorySource(gameDir)
        val files = managedPaths(manifest, state).mapNotNull { path -> disk.read(path)?.let { path to it } }.toMap()
        if (files.isEmpty()) return null
        val name = fileName(now, "manual")
        write(gameDir.resolve(GamePaths.BACKUPS), name, zip(files, emptyList(), "manual", now.toOffsetDateTime().toString()))
        return name
    }

    fun fileName(now: ZonedDateTime, reason: String): String =
        now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + "_" + reason + ".zip"

    /** Backup file names, newest first. */
    fun list(dir: Path): List<String> {
        if (!dir.isDirectory()) return emptyList()
        return dir.listDirectoryEntries("*.zip").map { it.name }.sortedDescending()
    }

    fun write(dir: Path, name: String, bytes: ByteArray) {
        dir.createDirectories()
        Files.write(dir.resolve(name), bytes)
        list(dir).drop(KEEP).forEach { dir.resolve(it).deleteIfExists() }
    }

    fun now(): ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
}
