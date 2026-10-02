package me.kmsold.proupdater.screen

import com.mojang.blaze3d.platform.InputConstants
import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.core.KeyNames
import me.kmsold.proupdater.steps.OptionKind
import me.kmsold.proupdater.steps.SetupStep
import me.kmsold.proupdater.steps.StepOption
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/** The keys and switches of one step, in two columns. Click a key, press the new one; Escape unbinds. */
class StepOptionsScreen(
    private val parent: Screen,
    private val step: SetupStep,
    private val values: MutableMap<String, String>,
) : Screen(Component.translatable("proupdater.step.${step.id}")) {

    /** The option waiting for a key press. */
    private var listening: StepOption? = null

    override fun init() {
        val columnWidth = 150
        val gap = 10
        val left = width / 2 - columnWidth - gap / 2
        val perColumn = (step.options.size + 1) / 2
        var top = maxOf(6, (height - (24 + perColumn * ROW + 32)) / 2)

        addRenderableWidget(StringWidget(left, top, columnWidth * 2 + gap, 9, title, font))
        top += 20

        step.options.forEachIndexed { index, option ->
            val x = left + (index / perColumn) * (columnWidth + gap)
            val y = top + (index % perColumn) * ROW
            val label = Component.translatable("proupdater.step.${step.id}.option.${option.id}")
            when (option.kind) {
                OptionKind.TOGGLE -> addRenderableWidget(
                    CycleButton.onOffBuilder(values[option.id] == "true")
                        .create(x, y, columnWidth, 20, label) { _, value -> values[option.id] = value.toString() },
                )
                OptionKind.KEY -> {
                    addRenderableWidget(StringWidget(x, y + 6, KEY_LABEL, 9, label, font))
                    addRenderableWidget(
                        Button.builder(keyText(option)) {
                            listening = option
                            rebuildWidgets()
                        }.bounds(x + KEY_LABEL + 4, y, columnWidth - KEY_LABEL - 4, 20).build(),
                    )
                }
            }
        }

        val bottom = top + perColumn * ROW + 8
        val half = columnWidth
        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.screen.resetDefaults")) {
                values.clear()
                values.putAll(step.defaultOptions())
                listening = null
                rebuildWidgets()
            }.bounds(left, bottom, half, 20).build(),
        )
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }.bounds(left + half + gap, bottom, half, 20).build())
    }

    private fun keyText(option: StepOption): Component {
        val name = values[option.id] ?: option.default
        val text = InputConstants.getKey(name).displayName.copy()
        if (listening == option) {
            return Component.literal("> ").append(text.withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)).append(" <")
                .withStyle(ChatFormatting.YELLOW)
        }
        val clash = name != KeyNames.UNBOUND &&
            step.options.any { it != option && it.kind == OptionKind.KEY && values[it.id] == name }
        return if (clash) text.withStyle(ChatFormatting.RED) else text
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val option = listening ?: return super.keyPressed(event)
        val name = if (event.key() == ESCAPE) KeyNames.UNBOUND else InputConstants.getKey(event).name
        // Keys that end up as GLFW codes in a mod config must be plain keyboard keys.
        if (!option.glfwOnly || name == KeyNames.UNBOUND || KeyNames.glfwCode(name) != null) {
            values[option.id] = name
        }
        listening = null
        rebuildWidgets()
        return true
    }

    override fun onClose() = McCompat.setScreen(parent)

    private companion object {
        const val ROW = 24
        const val KEY_LABEL = 84
        const val ESCAPE = 256
    }
}
