package me.kmsold.proupdater.core

/** How one file from `defaults/` lands in the game folder. Chosen per file, so new strategies slot in here. */
enum class FileStrategy {
    /** Overwrite whatever the player has. */
    REPLACE,

    /** Copy only when the player has no such file. */
    KEEP_EXISTING,

    /**
     * For `options.txt`: copy when missing, otherwise add only the pack keys the player's file lacks.
     * Modrinth App may write a synced `options.txt` before the first launch; its values win.
     */
    ADD_MISSING_OPTIONS,
}

enum class PlanKind { CLEAN_INSTALL, UPDATE, APPLY_ALL }

data class FileAction(val path: String, val strategy: FileStrategy)

object FilePlan {

    fun plan(kind: PlanKind, manifest: Manifest): List<FileAction> =
        manifest.payload().keys.sorted().map { FileAction(it, strategyFor(it, kind)) }

    fun strategyFor(path: String, kind: PlanKind): FileStrategy = when (kind) {
        PlanKind.UPDATE -> FileStrategy.KEEP_EXISTING
        PlanKind.APPLY_ALL -> FileStrategy.REPLACE
        PlanKind.CLEAN_INSTALL ->
            if (path == GamePaths.OPTIONS) FileStrategy.ADD_MISSING_OPTIONS else FileStrategy.REPLACE
    }

    /**
     * Runs [actions] against [files], reading the pack copies from `defaults/`.
     * Returns the files that were written, mapped to the SHA-1 of the pack copy.
     */
    fun execute(actions: List<FileAction>, files: FileChanges, log: (String) -> Unit): Map<String, String> {
        val applied = linkedMapOf<String, String>()
        for (action in actions) {
            val source = files.read(GamePaths.defaultsPath(action.path))
            if (source == null) {
                log("${action.path} is in the manifest but not in ${GamePaths.DEFAULTS}, skipped")
                continue
            }
            val existing = files.read(action.path)
            val result: ByteArray? = when {
                existing == null -> source
                action.strategy == FileStrategy.REPLACE -> source
                action.strategy == FileStrategy.KEEP_EXISTING -> null
                else -> OptionsFile.parse(existing.toString(Charsets.UTF_8))
                    .addMissingFrom(OptionsFile.parse(source.toString(Charsets.UTF_8)))
                    .toText().toByteArray(Charsets.UTF_8)
            }
            if (result != null) {
                files.write(action.path, result)
                applied[action.path] = Hashes.sha1(source)
            }
        }
        return applied
    }
}

object Hashes {
    fun sha1(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
