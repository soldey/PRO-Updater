package me.kmsold.proupdater.screen

import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.core.Panel
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/**
 * Opened from Mod Menu and with `/proupdater`. Every button only queues work in `pending.json`;
 * the settings change on the next game start.
 */
class ProUpdaterScreen(
    private val parent: Screen?,
    private val panel: Panel = Panel(FabricLoader.getInstance().gameDir, FabricLoader.getInstance()::isModLoaded),
) : Screen(Component.translatable("proupdater.screen.title")) {

    override fun init() {
        val left = width / 2 - WIDTH / 2
        val rows = panel.steps.size + 3
        var y = maxOf(6, (height - (52 + rows * ROW + 24)) / 2)

        addRenderableWidget(StringWidget(left, y, WIDTH, 9, title, font))
        y += 14
        for (line in statusLines()) {
            addRenderableWidget(StringWidget(left, y, WIDTH, 9, line, font))
            y += 11
        }
        y += 6

        for (step in panel.steps) {
            val available = panel.isAvailable(step)
            var label: Component = Component.translatable("proupdater.step.${step.id}")
            if (available && panel.isNew(step)) {
                label = label.copy().append(Component.translatable("proupdater.screen.new").withStyle(ChatFormatting.YELLOW))
            }
            val toggleWidth = if (step.options.isEmpty()) WIDTH else WIDTH - OPTIONS_WIDTH - 4
            val toggle = CycleButton.onOffBuilder(panel.selected[step.id] == true && available)
                .create(left, y, toggleWidth, 20, label) { _, value -> panel.selected[step.id] = value }
            toggle.active = available
            toggle.setTooltip(
                Tooltip.create(
                    if (available) {
                        Component.translatable("proupdater.step.${step.id}.description")
                    } else {
                        Component.translatable("proupdater.screen.needsMods", step.requiredMods.joinToString())
                    },
                ),
            )
            addRenderableWidget(toggle)
            if (step.options.isNotEmpty()) {
                val button = Button.builder(Component.translatable("proupdater.screen.options")) {
                    McCompat.setScreen(StepOptionsScreen(this, step, panel.options.getValue(step.id)))
                }.bounds(left + toggleWidth + 4, y, OPTIONS_WIDTH, 20).build()
                button.active = available
                addRenderableWidget(button)
            }
            y += ROW
        }
        y += 4

        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.screen.queueSteps")) {
                panel.queueSelectedSteps()
                rebuildWidgets()
            }.bounds(left, y, WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("proupdater.screen.queueSteps.tooltip")))
                .build(),
        )
        y += ROW

        val half = (WIDTH - 4) / 2
        val applyPack = Button.builder(Component.translatable("proupdater.screen.applyPack")) {
            panel.queueApplyPackDefaults()
            rebuildWidgets()
        }.bounds(left, y, half, 20)
            .tooltip(Tooltip.create(Component.translatable("proupdater.screen.applyPack.tooltip")))
            .build()
        applyPack.active = panel.manifest != null
        addRenderableWidget(applyPack)
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

        addRenderableWidget(StringWidget(left, y, WIDTH, 9, queueLine(), font))
    }

    private fun statusLines(): List<Component> {
        val unknown = Component.translatable("proupdater.screen.unknown")
        val installed = panel.state?.packVersion?.let(Component::literal) ?: unknown
        val inPack = panel.manifest?.version?.let(Component::literal) ?: unknown
        val cleanInstall = panel.state?.cleanInstallAt?.let { Component.literal(it.take(16).replace('T', ' ')) } ?: unknown
        return listOf(
            Component.translatable("proupdater.screen.versions", installed, inPack).withStyle(ChatFormatting.GRAY),
            Component.translatable("proupdater.screen.cleanInstall", cleanInstall).withStyle(ChatFormatting.GRAY),
        )
    }

    private fun queueLine(): Component {
        val pending = panel.pending
        if (pending.isEmpty()) return Component.translatable("proupdater.screen.queue.empty").withStyle(ChatFormatting.GRAY)
        val parts = buildList {
            pending.restoreBackup?.let { add(Component.translatable("proupdater.screen.queue.restore", it)) }
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

    override fun onClose() = McCompat.setScreen(parent)

    private companion object {
        const val WIDTH = 310
        const val OPTIONS_WIDTH = 76
        const val ROW = 24
    }
}
