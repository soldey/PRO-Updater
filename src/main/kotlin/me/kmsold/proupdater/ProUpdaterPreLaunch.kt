package me.kmsold.proupdater

import me.kmsold.proupdater.core.Installer
import me.kmsold.proupdater.core.RunReport
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.slf4j.LoggerFactory

/**
 * Lays out the pack settings before Minecraft reads `options.txt` and before the other mods load
 * their configs. Must not touch Minecraft classes: loading them this early would keep other mods'
 * mixins from applying.
 */
class ProUpdaterPreLaunch : PreLaunchEntrypoint {

    override fun onPreLaunch() {
        val loader = FabricLoader.getInstance()
        val logger = LoggerFactory.getLogger("PRO-Updater")
        LastRun.report = runCatching { Installer(loader.gameDir, loader::isModLoaded).run() }
            .onFailure {
                logger.error("Could not lay out the pack settings, the game starts with the files as they are", it)
                LastRun.failed = true
            }
            .getOrNull()
            ?.also { report -> report.log.forEach(logger::info) }
    }
}

/** Handed from the pre-launch run to the client, which shows the notices in chat. */
object LastRun {
    @Volatile
    var report: RunReport? = null

    @Volatile
    var failed: Boolean = false
}
