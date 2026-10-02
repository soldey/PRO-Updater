package me.kmsold.proupdater.core

import me.kmsold.proupdater.steps.SetupStep
import me.kmsold.proupdater.steps.Steps
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * What the screens show and queue, without any Minecraft classes. Nothing here writes the settings
 * files: every action goes into `pending.json` for the next launch, because the mods hold their
 * configs in memory and would write them back on exit. Only a manual backup happens at once.
 */
class Panel(private val gameDir: Path, private val isModLoaded: (String) -> Boolean) {

    private fun readOrNull(path: String): String? =
        gameDir.resolve(path).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    val manifest: Manifest? = readOrNull(GamePaths.MANIFEST)?.let(ModelJson::manifest)
    val state: InstallState? = readOrNull(GamePaths.STATE)?.let(ModelJson::state)
    private val packList: List<String>? = readOrNull(GamePaths.RESOURCE_PACKS)?.let(ModelJson::resourcePacks)

    var pending: PendingActions = readOrNull(GamePaths.PENDING)?.let(ModelJson::pending) ?: PendingActions()
        private set

    val steps: List<SetupStep> = Steps.all

    /** Which steps are ticked; starts with what is queued, or with the steps that are new for this player. */
    val selected: MutableMap<String, Boolean> = steps.associate { step ->
        step.id to (pending.steps.any { it.id == step.id } || (isAvailable(step) && isNew(step)))
    }.toMutableMap()

    /** Option values per step: queued ones, else the last applied ones, else the defaults. */
    val options: Map<String, MutableMap<String, String>> = steps.associate { step ->
        val saved = pending.steps.firstOrNull { it.id == step.id }?.options ?: state?.stepOptions?.get(step.id) ?: emptyMap()
        step.id to step.resolve(saved).toMutableMap()
    }

    fun isAvailable(step: SetupStep) = step.isAvailable(isModLoaded)

    fun isNew(step: SetupStep) = Steps.isNew(step, state, packList)

    fun backups(): List<String> = Backups.list(gameDir.resolve(GamePaths.BACKUPS))

    /**
     * Saves the settings files right away; reading them while the game runs is harmless. Changes a
     * mod still holds in memory are not in it. Returns the backup name, null when there was
     * nothing to save; throws when writing failed.
     */
    fun makeBackup(): String? = Backups.makeManual(gameDir, manifest, state, Backups.now())

    /** Whether the setup screen has something new for this player. */
    fun hasNewSteps() = steps.any { isAvailable(it) && isNew(it) }

    /** Replaces the queued steps with the ticked, available ones. */
    fun queueSelectedSteps() {
        pending.steps = steps.filter { selected[it.id] == true && isAvailable(it) }
            .map { PendingStep.of(it.id, options.getValue(it.id).toMap()) }
            .toMutableList()
        save()
    }

    fun queueApplyPackDefaults() {
        pending.applyPackDefaults = true
        save()
    }

    fun queueRestore(backup: String) {
        pending.restoreBackup = backup
        save()
    }

    fun clearQueue() {
        pending = PendingActions()
        save()
    }

    private fun save() {
        val file = gameDir.resolve(GamePaths.PENDING)
        if (pending.isEmpty()) {
            file.deleteIfExists()
        } else {
            DirectorySource(gameDir).commit(mapOf(GamePaths.PENDING to ModelJson.write(pending).toByteArray(Charsets.UTF_8)))
        }
    }
}
