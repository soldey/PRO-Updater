package me.kmsold.proupdater.screen

import me.kmsold.proupdater.ProUpdater
import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.core.FirstRunChoice
import me.kmsold.proupdater.core.Panel
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Shown instead of the main menu after a clean install of the pack, until the player picks one of
 * three ways to set up. The author's preset is already in place, so keeping it needs no restart.
 */
class WelcomeScreen(
    private val parent: Screen,
    private val panel: Panel = Panel(FabricLoader.getInstance().gameDir, FabricLoader.getInstance()::isModLoaded),
) : Screen(Component.translatable("proupdater.welcome.title")) {

    private data class Choice(val key: String, val action: () -> Unit)

    private val choices = listOf(
        Choice("author") {
            runCatching { panel.keepAuthorPreset() }.onFailure { ProUpdater.logger.error("Could not save the first-run choice", it) }
            McCompat.setScreen(parent)
        },
        Choice("custom") {
            panel.selectAuthorDefaults()
            McCompat.setScreen(
                SetupWizardScreen(this, panel) {
                    panel.queueFirstRunChoice(FirstRunChoice.CUSTOM)
                    McCompat.setScreen(RestartScreen(parent))
                },
            )
        },
        Choice("none") {
            panel.queueFirstRunChoice(FirstRunChoice.NONE)
            McCompat.setScreen(RestartScreen(parent))
        },
    )

    override fun init() {
        val left = width / 2 - WIDTH / 2
        val version = panel.manifest?.version ?: ""
        val intro = MultiLineTextWidget(left, 0, Component.translatable("proupdater.welcome.intro"), font).setMaxWidth(WIDTH)
        val descriptions = choices.map {
            MultiLineTextWidget(left, 0, Component.translatable("proupdater.welcome.${it.key}.description").withStyle(ChatFormatting.GRAY), font)
                .setMaxWidth(WIDTH)
        }
        val total = 16 + intro.height + 12 + descriptions.sumOf { 22 + it.height + 10 } + 12
        var y = maxOf(6, (height - total) / 2)

        addRenderableWidget(
            StringWidget(left, y, WIDTH, 9, Component.translatable("proupdater.welcome.title", version).withStyle(ChatFormatting.BOLD), font),
        )
        y += 16
        intro.setY(y)
        addRenderableWidget(intro)
        y += intro.height + 12

        choices.forEachIndexed { index, choice ->
            addRenderableWidget(
                Button.builder(Component.translatable("proupdater.welcome.${choice.key}")) { choice.action() }
                    .bounds(left, y, WIDTH, 20).build(),
            )
            y += 22
            descriptions[index].setY(y)
            addRenderableWidget(descriptions[index])
            y += descriptions[index].height + 10
        }
        y += 2
        addRenderableWidget(
            StringWidget(left, y, WIDTH, 9, Component.translatable("proupdater.welcome.later").withStyle(ChatFormatting.DARK_GRAY), font),
        )
    }

    /** A choice has to be made; Escape would only bring the screen back on the next start. */
    override fun shouldCloseOnEsc() = false

    private companion object {
        const val WIDTH = 320
    }
}
