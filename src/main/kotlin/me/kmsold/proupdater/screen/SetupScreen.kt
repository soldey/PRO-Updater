package me.kmsold.proupdater.screen

import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.core.Panel
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/** The setup steps: tick them, set their keys, queue them for the next start. */
class SetupScreen(private val parent: Screen, private val panel: Panel) :
    Screen(Component.translatable("proupdater.setup.title")) {

    override fun init() {
        val left = width / 2 - WIDTH / 2
        var y = maxOf(6, (height - (34 + (panel.steps.size + 2) * ROW + 16)) / 2)

        addRenderableWidget(StringWidget(left, y, WIDTH, 9, title, font))
        y += 12
        addRenderableWidget(
            StringWidget(left, y, WIDTH, 9, Component.translatable("proupdater.setup.hint").withStyle(ChatFormatting.GRAY), font),
        )
        y += 18

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

        val half = (WIDTH - 4) / 2
        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.screen.queueSteps")) {
                panel.queueSelectedSteps()
                rebuildWidgets()
            }.bounds(left, y, half, 20)
                .tooltip(Tooltip.create(Component.translatable("proupdater.screen.queueSteps.tooltip")))
                .build(),
        )
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK) { onClose() }.bounds(left + half + 4, y, half, 20).build())
        y += ROW + 2

        addRenderableWidget(StringWidget(left, y, WIDTH, 9, ProUpdaterScreen.queueLine(panel), font))
    }

    override fun onClose() = McCompat.setScreen(parent)

    private companion object {
        const val WIDTH = 310
        const val OPTIONS_WIDTH = 76
        const val ROW = 24
    }
}
