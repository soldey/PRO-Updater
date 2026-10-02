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
) {
    private val disk = DirectorySource(gameDir)

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

        val mode = LaunchMode.detect(oldState, manifest, disk::exists)
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
                context.notice("proupdater.notice.installed", manifest.version)
                reasons += "clean-install"
            }
            LaunchMode.LEGACY_MIGRATION, LaunchMode.UPDATE -> {
                layOut(PlanKind.UPDATE, manifest!!, files, state, context)
                for (migration in Migrations.all.filter { it.id !in state.migrations }) {
                    context.log("Running migration ${migration.id}")
                    migration.apply(context)
                    state.migrations += migration.id
                }
                state.packVersion = manifest.version
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
        if (originals.isNotEmpty()) {
            backupName = Backups.fileName(timestamp, reasons.joinToString("+").ifEmpty { "run" })
            val zip = Backups.zip(originals, files.created(), reasons.joinToString(", "), timestamp.toOffsetDateTime().toString())
            Backups.write(gameDir.resolve(GamePaths.BACKUPS), backupName, zip)
            context.log("Backed up ${originals.size} file(s) to ${GamePaths.BACKUPS}/$backupName")
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
        pending.restoreBackup?.let { name ->
            val zip = gameDir.resolve(GamePaths.BACKUPS).resolve(name)
            val contents = if (GamePaths.isSafe(name) && '/' !in name) runCatching { Backups.unzip(zip.readBytes()) }.getOrNull() else null
            if (contents == null) {
                context.log("Backup $name is missing or unreadable, nothing restored")
                context.notice("proupdater.notice.restoreFailed", name)
            } else {
                Backups.restore(contents, files)
                context.log("Restored backup $name")
                context.notice("proupdater.notice.restored", name)
            }
        }
        if (pending.applyPackDefaults) {
            if (manifest == null) {
                context.log("Apply pack settings asked for, but there is no ${GamePaths.MANIFEST}")
            } else {
                layOut(PlanKind.APPLY_ALL, manifest, files, state, context)
                applyDefaultSteps(context, state)
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
}
