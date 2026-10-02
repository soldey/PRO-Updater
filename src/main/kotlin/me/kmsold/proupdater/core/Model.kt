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
}

/** `proupdater/pending.json`: what the player asked for on the screen, done on the next launch. */
class PendingActions {
    var restoreBackup: String? = null
    var applyPackDefaults: Boolean = false
    var steps: MutableList<PendingStep> = mutableListOf()

    fun isEmpty() = restoreBackup == null && !applyPackDefaults && steps.isEmpty()
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
