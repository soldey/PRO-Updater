package me.kmsold.proupdater.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

/**
 * `proupdater/defaults/manifest.json`, written by the pack build:
 * `{"version": "3.4.0", "files": {"config/skyblocker.json": "<sha1>", "options.txt": "<sha1>"}}`.
 */
class Manifest {
    var version: String = ""
    var files: Map<String, String> = emptyMap()

    /** The payload files, without anything that could escape the game folder. */
    fun payload(): Map<String, String> = files.filterKeys { GamePaths.isSafe(it) && it !in GamePaths.META_FILES }
}

/** `proupdater/state.json`: what the mod has done in this profile so far. */
class InstallState {
    /** The pack version whose settings were laid out last; null until a manifest has been seen. */
    var packVersion: String? = null
    var cleanInstallAt: String? = null
    var lastRunAt: String? = null

    /** SHA-1 of every file copied from `defaults/`, as it was copied. */
    var files: MutableMap<String, String> = mutableMapOf()

    /** Step id to the step version that was applied or already offered to the player. */
    var steps: MutableMap<String, Int> = mutableMapOf()

    /** The options each step was last applied with, so the screen can show them again. */
    var stepOptions: MutableMap<String, Map<String, String>> = mutableMapOf()

    /** Resource packs from `resourcepacks.json` the player has already been given. */
    var resourcePacks: MutableList<String> = mutableListOf()

    /** One-time migrations that ran, or were skipped because the install was fresh. */
    var migrations: MutableList<String> = mutableListOf()

    /** After a clean install, until the player picks how to set up: the welcome screen replaces the main menu. */
    var welcomePending: Boolean = false

    /** The backup taken by the clean install; restoring it undoes the install. */
    var cleanInstallBackup: String? = null

    /** The player's choice on the welcome screen, see [FirstRunChoice]. With `none` updates add no pack files. */
    var preset: String? = null
}

/** What the player picked on the welcome screen after a clean install. */
object FirstRunChoice {
    /** The author's setup, already laid out by the clean install. */
    const val AUTHOR = "author"

    /** The pack files again, with only the steps the player picked, on the next start. */
    const val CUSTOM = "custom"

    /** The clean install undone on the next start: every mod keeps its own defaults. */
    const val NONE = "none"
}

/** `proupdater/pending.json`: what the player asked for on the screen, done on the next launch. */
class PendingActions {
    /** [FirstRunChoice.CUSTOM] or [FirstRunChoice.NONE] from the welcome screen; handled before everything else. */
    var firstRunChoice: String? = null
    var restoreBackup: String? = null

    /** Only some mods' settings from a backup, after [restoreBackup]. */
    var modRestore: ModRestore? = null
    var applyPackDefaults: Boolean = false
    var steps: MutableList<PendingStep> = mutableListOf()

    fun isEmpty() = firstRunChoice == null && restoreBackup == null && modRestore == null && !applyPackDefaults && steps.isEmpty()
}

class ModRestore {
    var backup: String = ""

    /** The files to take from the backup, worked out when the player picked the mods. */
    var paths: List<String> = emptyList()

    /** The picked mods' names, for the screen and the chat. */
    var mods: List<String> = emptyList()

    companion object {
        fun of(backup: String, paths: List<String>, mods: List<String>) = ModRestore().also {
            it.backup = backup
            it.paths = paths
            it.mods = mods
        }
    }
}

class PendingStep {
    var id: String = ""
    var options: Map<String, String> = emptyMap()

    companion object {
        fun of(id: String, options: Map<String, String>) = PendingStep().also {
            it.id = id
            it.options = options
        }
    }
}

object ModelJson {

    val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun manifest(text: String): Manifest? = parse(text, Manifest::class.java)?.takeIf { it.version.isNotBlank() }

    fun state(text: String): InstallState? = parse(text, InstallState::class.java)

    fun pending(text: String): PendingActions? = parse(text, PendingActions::class.java)

    fun resourcePacks(text: String): List<String>? = runCatching {
        gson.fromJson<List<String>>(text, object : TypeToken<List<String>>() {}.type)?.filterNotNull()
    }.getOrNull()

    fun write(value: Any): String = gson.toJson(value) + "\n"

    /** Gson leaves fields missing from the file at the defaults of the no-arg constructor; nulls are fixed up here. */
    private fun <T : Any> parse(text: String, type: Class<T>): T? = runCatching { gson.fromJson(text, type) }
        .getOrNull()
        ?.also(::repairNulls)

    @Suppress("SENSELESS_COMPARISON")
    private fun repairNulls(value: Any) {
        when (value) {
            is Manifest -> {
                if (value.version == null) value.version = ""
                if (value.files == null) value.files = emptyMap()
            }
            is InstallState -> {
                if (value.files == null) value.files = mutableMapOf()
                if (value.steps == null) value.steps = mutableMapOf()
                if (value.stepOptions == null) value.stepOptions = mutableMapOf()
                if (value.resourcePacks == null) value.resourcePacks = mutableListOf()
                if (value.migrations == null) value.migrations = mutableListOf()
            }
            is PendingActions -> {
                value.modRestore?.let {
                    if (it.backup == null || it.paths == null) value.modRestore = null
                    else if (it.mods == null) it.mods = emptyList()
                }
                if (value.steps == null) value.steps = mutableListOf()
                value.steps.removeAll { it == null || it.id.isNullOrBlank() }
                value.steps.forEach { if (it.options == null) it.options = emptyMap() }
            }
        }
    }
}

/** Dotted numeric versions; anything after a `-` or `+` is ignored, missing parts count as 0. */
object Versions {
    fun compare(a: String, b: String): Int {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").split('-', '+').first().split('.').map { it.toIntOrNull() ?: 0 }
}
