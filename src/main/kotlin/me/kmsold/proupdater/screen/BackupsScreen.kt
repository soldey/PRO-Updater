package me.kmsold.proupdater.screen

import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.core.Panel
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/**
 * The newest backups. [onPick] decides what picking one does: queue a full restore, or go on to
 * choose which mods to take from it. [highlighted] marks the backup that is already queued.
 */
class BackupsScreen(
    private val parent: Screen,
    private val panel: Panel,
    title: Component,
    private val hint: Component,
    private val highlighted: String? = null,
    private val onPick: (String) -> Unit,
) : Screen(title) {

    override fun init() {
        val left = width / 2 - WIDTH / 2
        val backups = panel.backups().take(SHOWN)
        var y = maxOf(6, (height - (40 + maxOf(backups.size, 1) * ROW + 28)) / 2)

        addRenderableWidget(StringWidget(left, y, WIDTH, 9, title, font))
        y += 12
        addRenderableWidget(StringWidget(left, y, WIDTH, 9, hint.copy().withStyle(ChatFormatting.GRAY), font))
        y += 18

        if (backups.isEmpty()) {
            addRenderableWidget(StringWidget(left, y + 6, WIDTH, 9, Component.translatable("proupdater.backups.none"), font))
            y += ROW
        }
        for (name in backups) {
            val label = Component.literal(name.removeSuffix(".zip"))
            val shown = if (name == highlighted) label.withStyle(ChatFormatting.YELLOW) else label
            addRenderableWidget(Button.builder(shown) { onPick(name) }.bounds(left, y, WIDTH, 20).build())
            y += ROW
        }
        y += 4
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK) { onClose() }.bounds(left, y, WIDTH, 20).build())
    }

    override fun onClose() = McCompat.setScreen(parent)

    private companion object {
        const val WIDTH = 310
        const val ROW = 24
        const val SHOWN = 8
    }
}
