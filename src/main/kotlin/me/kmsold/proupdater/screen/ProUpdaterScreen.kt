package me.kmsold.proupdater.screen

import me.kmsold.proupdater.ProUpdater
import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.core.Panel
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/**
 * The mod's main screen, opened from Mod Menu and with `/proupdater`: the pack status, the
 * setup steps behind their own button, and backups. Everything except a manual backup is queued
 * in `pending.json` and happens on the next game start.
 */
class ProUpdaterScreen(
    private val parent: Screen?,
    private val panel: Panel = Panel(FabricLoader.getInstance().gameDir, FabricLoader.getInstance()::isModLoaded),
) : Screen(Component.translatable("proupdater.screen.title")) {

    /** The outcome of the last manual backup, kept across widget rebuilds. */
    private var message: Component? = null

    override fun init() {
        val left = width / 2 - WIDTH / 2
        val half = (WIDTH - 4) / 2
        var y = maxOf(6, (height - (42 + 4 * ROW + 26)) / 2)

        addRenderableWidget(StringWidget(left, y, WIDTH, 9, title, font))
        y += 14
        for (line in statusLines()) {
            addRenderableWidget(StringWidget(left, y, WIDTH, 9, line, font))
            y += 11
        }
        y += 6

        var setupLabel: Component = Component.translatable("proupdater.screen.setup")
        if (panel.hasNewSteps()) {
            setupLabel = setupLabel.copy().append(Component.translatable("proupdater.screen.new").withStyle(ChatFormatting.YELLOW))
        }
        addRenderableWidget(
            Button.builder(setupLabel) { McCompat.setScreen(SetupWizardScreen(this, panel)) }
                .bounds(left, y, WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("proupdater.screen.setup.tooltip")))
                .build(),
        )
        y += ROW

        val applyPack = Button.builder(Component.translatable("proupdater.screen.applyPack")) {
            panel.queueApplyPackDefaults()
            rebuildWidgets()
        }.bounds(left, y, WIDTH, 20)
            .tooltip(Tooltip.create(Component.translatable("proupdater.screen.applyPack.tooltip")))
            .build()
        applyPack.active = panel.manifest != null
        addRenderableWidget(applyPack)
        y += ROW

        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.screen.makeBackup")) { makeBackup() }
                .bounds(left, y, half, 20)
                .tooltip(Tooltip.create(Component.translatable("proupdater.screen.makeBackup.tooltip")))
                .build(),
        )
        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.screen.restore")) {
                McCompat.setScreen(BackupsScreen(this, panel))
            }.bounds(left + half + 4, y, half, 20).build(),
        )
        y += ROW

        val clear = Button.builder(Component.translatable("proupdater.screen.clearQueue")) {
            panel.clearQueue()
            rebuildWidgets()
        }.bounds(left, y, half, 20).build()
        clear.active = !panel.pending.isEmpty()
        addRenderableWidget(clear)
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }.bounds(left + half + 4, y, half, 20).build())
        y += ROW + 2

        addRenderableWidget(StringWidget(left, y, WIDTH, 9, queueLine(panel), font))
        message?.let { addRenderableWidget(StringWidget(left, y + 12, WIDTH, 9, it, font)) }
    }

    private fun makeBackup() {
        message = runCatching { panel.makeBackup() }
            .fold(
                onSuccess = { name ->
                    if (name == null) {
                        Component.translatable("proupdater.screen.makeBackup.nothing").withStyle(ChatFormatting.GRAY)
                    } else {
                        Component.translatable("proupdater.screen.makeBackup.done", name.removeSuffix(".zip"))
                            .withStyle(ChatFormatting.GREEN)
                    }
                },
                onFailure = {
                    ProUpdater.logger.error("Could not make a backup", it)
                    Component.translatable("proupdater.screen.makeBackup.failed").withStyle(ChatFormatting.RED)
                },
            )
        rebuildWidgets()
    }

    private fun statusLines(): List<Component> {
        val unknown = Component.translatable("proupdater.screen.unknown")
        val installed = panel.state?.packVersion?.let(Component::literal) ?: unknown
        val inPack = panel.manifest?.version?.let(Component::literal) ?: unknown
        val cleanInstall = panel.state?.cleanInstallAt?.let { Component.literal(it.take(16).replace('T', ' ')) } ?: unknown
        val preset = panel.state?.preset?.let { Component.translatable("proupdater.screen.preset.$it") } ?: unknown
        return listOf(
            Component.translatable("proupdater.screen.versions", installed, inPack).withStyle(ChatFormatting.GRAY),
            Component.translatable("proupdater.screen.cleanInstall", cleanInstall, preset).withStyle(ChatFormatting.GRAY),
        )
    }

    override fun onClose() = McCompat.setScreen(parent)

    companion object {
        private const val WIDTH = 310
        private const val ROW = 24

        /** What is waiting for the next start, shared with the setup screen. */
        fun queueLine(panel: Panel): Component {
            val pending = panel.pending
            if (pending.isEmpty()) return Component.translatable("proupdater.screen.queue.empty").withStyle(ChatFormatting.GRAY)
            val parts = buildList {
                pending.firstRunChoice?.let { add(Component.translatable("proupdater.screen.queue.firstRun.$it")) }
                pending.restoreBackup?.let { add(Component.translatable("proupdater.screen.queue.restore", it.removeSuffix(".zip"))) }
                if (pending.applyPackDefaults) add(Component.translatable("proupdater.screen.queue.applyPack"))
                if (pending.steps.isNotEmpty()) add(Component.translatable("proupdater.screen.queue.steps", pending.steps.size.toString()))
            }
            val joined = Component.empty()
            parts.forEachIndexed { i, part ->
                if (i > 0) joined.append(", ")
                joined.append(part)
            }
            return Component.translatable("proupdater.screen.queue", joined).withStyle(ChatFormatting.YELLOW)
        }
    }
}
