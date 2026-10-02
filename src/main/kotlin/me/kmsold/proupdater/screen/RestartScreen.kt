package me.kmsold.proupdater.screen

import me.kmsold.proupdater.compat.McCompat
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** After a choice that only takes effect on the next start: quit now, or carry on and restart later. */
class RestartScreen(private val parent: Screen) : Screen(Component.translatable("proupdater.restart.title")) {

    override fun init() {
        val left = width / 2 - WIDTH / 2
        val text = MultiLineTextWidget(left, 0, Component.translatable("proupdater.restart.text"), font).setMaxWidth(WIDTH)
        var y = maxOf(6, (height - (16 + text.height + 12 + 48)) / 2)

        addRenderableWidget(StringWidget(left, y, WIDTH, 9, title.copy().withStyle(ChatFormatting.BOLD), font))
        y += 16
        text.setY(y)
        addRenderableWidget(text)
        y += text.height + 12

        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.restart.quit")) { Minecraft.getInstance().stop() }
                .bounds(left, y, WIDTH, 20).build(),
        )
        y += 24
        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.restart.later")) { onClose() }
                .bounds(left, y, WIDTH, 20).build(),
        )
    }

    override fun onClose() = McCompat.setScreen(parent)

    private companion object {
        const val WIDTH = 300
    }
}
