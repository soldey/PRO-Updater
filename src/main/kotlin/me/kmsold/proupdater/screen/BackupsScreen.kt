package me.kmsold.proupdater.screen

import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.core.Panel
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/** The newest backups; picking one queues its restore for the next start. */
class BackupsScreen(private val parent: Screen, private val panel: Panel) :
    Screen(Component.translatable("proupdater.backups.title")) {

    override fun init() {
        val left = width / 2 - WIDTH / 2
        val backups = panel.backups().take(SHOWN)
        var y = maxOf(6, (height - (40 + maxOf(backups.size, 1) * ROW + 28)) / 2)

        addRenderableWidget(StringWidget(left, y, WIDTH, 9, title, font))
        y += 12
        addRenderableWidget(
            StringWidget(left, y, WIDTH, 9, Component.translatable("proupdater.backups.hint").withStyle(ChatFormatting.GRAY), font),
        )
        y += 18

        if (backups.isEmpty()) {
            addRenderableWidget(StringWidget(left, y + 6, WIDTH, 9, Component.translatable("proupdater.backups.none"), font))
            y += ROW
        }
        for (name in backups) {
            val label = Component.literal(name.removeSuffix(".zip"))
            val shown = if (name == panel.pending.restoreBackup) label.withStyle(ChatFormatting.YELLOW) else label
            addRenderableWidget(
                Button.builder(shown) {
                    panel.queueRestore(name)
                    onClose()
                }.bounds(left, y, WIDTH, 20).build(),
            )
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
