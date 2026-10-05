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

    /**
     * Which steps are switched on. With something queued, exactly the queued steps; otherwise the
     * steps that are new for this player or were never applied in this profile. Steps that wipe the
     * player's data, like clearing slot bindings, start off unless queued.
     */
    val selected: MutableMap<String, Boolean> = steps.associate { step ->
        val on = if (pending.steps.isNotEmpty()) {
            pending.steps.any { it.id == step.id }
        } else {
            isAvailable(step) && step.offeredOnUpdate && (isNew(step) || state?.steps?.containsKey(step.id) != true)
        }
        step.id to on
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

    /** A clean install happened and the player has not picked how to set up yet. */
    val welcomePending: Boolean get() = state?.welcomePending == true

    /** Switches on what the author's preset runs, with default keys: the starting point of a custom setup. */
    fun selectAuthorDefaults() {
        for (step in steps) {
            selected[step.id] = step.enabledByDefault && isAvailable(step)
            options.getValue(step.id).apply {
                clear()
                putAll(step.defaultOptions())
            }
        }
    }

    /** Keeps the author's setup the clean install already laid out. Takes effect at once, no restart. */
    fun keepAuthorPreset() {
        val updated = state ?: return
        updated.welcomePending = false
        updated.preset = FirstRunChoice.AUTHOR
        DirectorySource(gameDir).commit(mapOf(GamePaths.STATE to ModelJson.write(updated).toByteArray(Charsets.UTF_8)))
    }

    /**
     * Queues [FirstRunChoice.CUSTOM] with the switched-on steps, or [FirstRunChoice.NONE], for the
     * next start. Anything queued before is replaced.
     */
    fun queueFirstRunChoice(choice: String) {
        pending = PendingActions().also { it.firstRunChoice = choice }
        if (choice == FirstRunChoice.CUSTOM) queueSelectedSteps() else save()
    }

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

    /** The files backup [name] can change, or null when it cannot be read. */
    fun backupPaths(name: String): List<String>? {
        if (!GamePaths.isSafe(name) || '/' in name) return null
        val zip = gameDir.resolve(GamePaths.BACKUPS).resolve(name)
        return runCatching { Backups.unzip(java.nio.file.Files.readAllBytes(zip)) }.getOrNull()?.let(Backups::paths)
    }

    /** Queues the settings of [groups] from backup [name] for the next start. */
    fun queueModRestore(name: String, groups: List<ModGroup>) {
        pending.modRestore = ModRestore.of(name, groups.flatMap { it.paths }.distinct(), groups.map { it.name })
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
