package me.kmsold.proupdater.core

import me.kmsold.proupdater.steps.Notice
import me.kmsold.proupdater.steps.StepContext
import me.kmsold.proupdater.steps.Steps
import java.nio.file.Path
import java.time.ZonedDateTime
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

/** A one-time change for players updating from an older pack version. New installs only mark it as done. */
class Migration(val id: String, val apply: (StepContext) -> Unit)

object Migrations {
    /** None yet. Add `Migration("id") { ... }` entries here; they run once, in this order. */
    val all: List<Migration> = emptyList()
}

class RunReport(
    val mode: LaunchMode,
    val backup: String?,
    val changedFiles: Int,
    val notices: List<Notice>,
    val log: List<String>,
)

/**
 * Everything the mod does to the game folder, in one go before Minecraft starts. All writes are
 * collected first; when they would overwrite or delete anything, the old contents are zipped into
 * `proupdater/backups/` before a single file is touched.
 */
class Installer(
    private val gameDir: Path,
    private val isModLoaded: (String) -> Boolean,
    private val now: () -> ZonedDateTime = Backups::now,
    /** When this game process started, epoch milliseconds; files written after it are this launch's own. */
    private val launchStartedAt: Long = processStart(),
) {
    private val disk = DirectorySource(gameDir)

    /**
     * True when [path] was already there before this launch. Sodium, Lithium, C2ME, FerriteCore,
     * VMP and BadOptimizations create their config while their mixins load, before any pre-launch
     * code; on a fresh profile those files exist but say nothing about the player. File times can
     * be a second coarse, hence the margin.
     */
    private fun existedBefore(path: String, context: StepContext): Boolean {
        val modified = disk.modifiedAt(path) ?: return false
        if (modified < launchStartedAt - TIME_MARGIN_MS) return true
        context.log("$path was written during this launch, before PRO-Updater ran; not counted as an existing setup")
        return false
    }

    fun run(): RunReport {
        val files = FileChanges(disk)
        val context = StepContext(files, isModLoaded)
        val manifest = files.readText(GamePaths.MANIFEST)?.let { text ->
            ModelJson.manifest(text).also { if (it == null) context.log("${GamePaths.MANIFEST} is unreadable, ignored") }
        }
        val oldState = files.readText(GamePaths.STATE)?.let { text ->
            ModelJson.state(text).also { if (it == null) context.log("${GamePaths.STATE} is unreadable, starting over") }
        }
        val pending = files.readText(GamePaths.PENDING)?.let(ModelJson::pending)
        val state = oldState ?: InstallState()
        val timestamp = now()

        val mode = LaunchMode.detect(oldState, manifest) { existedBefore(it, context) }
        if (oldState?.packVersion == null && manifest != null) {
            val configs = manifest.payload().keys.filter { it.startsWith("config/") }
            context.log("${configs.count { disk.exists(it) }} of ${configs.size} pack config files are already in config/")
        }
        context.log("Launch mode: $mode (installed ${oldState?.packVersion ?: "none"}, pack ${manifest?.version ?: "none"})")
        val reasons = mutableListOf<String>()

        when (mode) {
            LaunchMode.CLEAN_INSTALL -> {
                layOut(PlanKind.CLEAN_INSTALL, manifest!!, files, state, context)
                applyDefaultSteps(context, state)
                // Everything that exists now is what a new player gets; nothing is offered as new.
                Steps.all.forEach { state.steps.putIfAbsent(it.id, it.version) }
                Migrations.all.forEach { if (it.id !in state.migrations) state.migrations += it.id }
                state.cleanInstallAt = timestamp.toOffsetDateTime().toString()
                state.packVersion = manifest.version
                // The author's setup is in place straight away; the welcome screen lets the player keep,
                // redo or undo it, which is why this run is always backed up.
                state.welcomePending = true
                state.preset = FirstRunChoice.AUTHOR
                reasons += "clean-install"
            }
            LaunchMode.LEGACY_MIGRATION, LaunchMode.UPDATE -> {
                if (state.preset == FirstRunChoice.NONE) {
                    context.log("The player chose to go without the pack settings, no pack files are added")
                } else {
                    layOut(PlanKind.UPDATE, manifest!!, files, state, context)
                }
                for (migration in Migrations.all.filter { it.id !in state.migrations }) {
                    context.log("Running migration ${migration.id}")
                    migration.apply(context)
                    state.migrations += migration.id
                }
                state.packVersion = manifest!!.version
                val packList = files.readText(GamePaths.RESOURCE_PACKS)?.let(ModelJson::resourcePacks)
                if (Steps.all.any { it.isAvailable(isModLoaded) && Steps.isNew(it, state, packList) }) {
                    context.notice("proupdater.notice.newSteps")
                }
                reasons += "update"
            }
            LaunchMode.UP_TO_DATE, LaunchMode.NO_MANIFEST -> Unit
        }

        if (pending != null && !pending.isEmpty()) {
            runPending(pending, manifest, files, state, context)
            reasons += "requested"
        }

        val changes = files.changed()
        val originals = files.originals()
        var backupName: String? = null
        if (originals.isNotEmpty() || (mode == LaunchMode.CLEAN_INSTALL && changes.isNotEmpty())) {
            backupName = Backups.fileName(timestamp, reasons.joinToString("+").ifEmpty { "run" })
            val zip = Backups.zip(originals, files.created(), reasons.joinToString(", "), timestamp.toOffsetDateTime().toString())
            Backups.write(gameDir.resolve(GamePaths.BACKUPS), backupName, zip)
            context.log("Backed up ${originals.size} file(s) to ${GamePaths.BACKUPS}/$backupName")
            if (mode == LaunchMode.CLEAN_INSTALL) state.cleanInstallBackup = backupName
        }
        disk.commit(changes)
        if (changes.isNotEmpty()) context.log("Wrote ${changes.size} file(s)")

        if (mode != LaunchMode.NO_MANIFEST || oldState != null || changes.isNotEmpty()) {
            state.lastRunAt = timestamp.toOffsetDateTime().toString()
            disk.commit(mapOf(GamePaths.STATE to ModelJson.write(state).toByteArray(Charsets.UTF_8)))
        }
        if (pending != null) gameDir.resolve(GamePaths.PENDING).deleteIfExists()

        return RunReport(mode, backupName, changes.size, context.notices.toList(), context.log.toList())
    }

    private fun layOut(kind: PlanKind, manifest: Manifest, files: FileChanges, state: InstallState, context: StepContext) {
        val applied = FilePlan.execute(FilePlan.plan(kind, manifest), files, context::log)
        state.files.putAll(applied)
        context.log("${kind.name.lowercase()}: ${applied.size} of ${manifest.payload().size} pack file(s) written")
        if (kind != PlanKind.UPDATE) holdAttack(files, context)
    }

    /**
     * Attack/Destroy starts on Hold with every full layout of the pack, whatever a synced
     * `options.txt` says; updates leave it to the player.
     */
    private fun holdAttack(files: FileChanges, context: StepContext) {
        if (files.editOptions { it["toggleAttack"] = "false" } == EditResult.CHANGED) {
            context.log("Attack/Destroy set to Hold")
        }
    }

    private fun applyDefaultSteps(context: StepContext, state: InstallState) {
        for (step in Steps.all.filter { it.enabledByDefault }) {
            if (!step.isAvailable(isModLoaded)) {
                context.log("Step ${step.id} skipped, needs ${step.requiredMods.joinToString()}")
                continue
            }
            Steps.run(step, step.defaultOptions(), context, state)
        }
    }

    private fun runPending(pending: PendingActions, manifest: Manifest?, files: FileChanges, state: InstallState, context: StepContext) {
        pending.firstRunChoice?.let { choice -> runFirstRunChoice(choice, manifest, files, state, context) }
        pending.restoreBackup?.let { name ->
            if (restore(name, files, context)) context.notice("proupdater.notice.restored", name)
        }
        pending.modRestore?.let { request ->
            if (restore(request.backup, files, context, request.paths.toSet())) {
                context.log("Restored ${request.mods.joinToString()} from ${request.backup}")
                context.notice("proupdater.notice.modsRestored", request.mods.joinToString(), request.backup)
            }
        }
        if (pending.applyPackDefaults) {
            if (manifest == null) {
                context.log("Apply pack settings asked for, but there is no ${GamePaths.MANIFEST}")
            } else {
                layOut(PlanKind.APPLY_ALL, manifest, files, state, context)
                applyDefaultSteps(context, state)
                state.preset = FirstRunChoice.AUTHOR
                context.notice("proupdater.notice.packApplied", manifest.version)
            }
        }
        for (request in pending.steps) {
            val step = Steps.byId(request.id)
            when {
                step == null -> context.log("Unknown step ${request.id} in pending.json, skipped")
                !step.isAvailable(isModLoaded) -> context.log("Step ${step.id} skipped, needs ${step.requiredMods.joinToString()}")
                else -> {
                    Steps.run(step, request.options, context, state)
                    context.log("Applied step ${step.id}")
                    context.notice("proupdater.notice.stepApplied", "proupdater.step.${step.id}")
                }
            }
        }
    }

    /**
     * The welcome screen's choice. Both undo the clean install first, so the result is the same as
     * if the player had chosen before it: [FirstRunChoice.CUSTOM] then lays the pack files out
     * again, without the default steps (the queued steps follow), [FirstRunChoice.NONE] stops there.
     */
    private fun runFirstRunChoice(choice: String, manifest: Manifest?, files: FileChanges, state: InstallState, context: StepContext) {
        if (choice != FirstRunChoice.CUSTOM && choice != FirstRunChoice.NONE) {
            context.log("Unknown first-run choice '$choice', ignored")
            return
        }
        val backup = state.cleanInstallBackup
        if (backup == null) {
            context.log("No clean install backup to undo, the files stay as they are")
        } else {
            restore(backup, files, context)
        }
        if (choice == FirstRunChoice.CUSTOM) {
            if (manifest == null) {
                context.log("Custom setup asked for, but there is no ${GamePaths.MANIFEST}")
            } else {
                layOut(PlanKind.CLEAN_INSTALL, manifest, files, state, context)
            }
        }
        context.log("First-run choice: $choice")
        state.preset = choice
        state.welcomePending = false
    }

    companion object {
        private const val TIME_MARGIN_MS = 2_000L

        /** The JVM start, or, without java.management, the process start; unknown counts every file as old. */
        fun processStart(): Long =
            runCatching { java.lang.management.ManagementFactory.getRuntimeMXBean().startTime }
                .recoverCatching { ProcessHandle.current().info().startInstant().get().toEpochMilli() }
                .getOrDefault(Long.MAX_VALUE)
    }

    /** Queues the contents of backup [name], or just [only], into [files]. Returns false when it could not be read. */
    private fun restore(name: String, files: FileChanges, context: StepContext, only: Set<String>? = null): Boolean {
        val zip = gameDir.resolve(GamePaths.BACKUPS).resolve(name)
        val contents = if (GamePaths.isSafe(name) && '/' !in name) runCatching { Backups.unzip(zip.readBytes()) }.getOrNull() else null
        if (contents == null) {
            context.log("Backup $name is missing or unreadable, nothing restored")
            context.notice("proupdater.notice.restoreFailed", name)
            return false
        }
        Backups.restore(contents, files, only)
        if (only == null) context.log("Restored backup $name")
        return true
    }
}
