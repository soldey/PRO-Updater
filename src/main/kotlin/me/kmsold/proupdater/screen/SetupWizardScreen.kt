package me.kmsold.proupdater.screen

import com.mojang.blaze3d.platform.InputConstants
import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.core.KeyNames
import me.kmsold.proupdater.core.Panel
import me.kmsold.proupdater.steps.OptionKind
import me.kmsold.proupdater.steps.SetupStep
import me.kmsold.proupdater.steps.StepOption
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/**
 * The setup as a wizard: one page per step with its description, a switch and its keys, then a
 * summary that queues the chosen steps for the next start. Steps whose mods are missing get no
 * page, only a line in the summary.
 *
 * [onApply] replaces what the summary's button does; the welcome screen uses it to queue a custom
 * first-run setup, where going without any step is a valid choice too.
 */
class SetupWizardScreen(
    private val parent: Screen,
    private val panel: Panel,
    private val onApply: (() -> Unit)? = null,
) : Screen(Component.translatable("proupdater.wizard.title")) {

    private val pages: List<SetupStep> = panel.steps.filter(panel::isAvailable)

    /** Index into [pages]; `pages.size` is the summary. */
    private var page = 0

    /** The key option waiting for a key press. */
    private var listening: StepOption? = null

    override fun init() {
        if (page < pages.size) initStep(pages[page]) else initSummary()
    }

    private fun initStep(step: SetupStep) {
        val selected = panel.selected[step.id] == true
        val values = panel.options.getValue(step.id)
        val columns = columnsFor(step)
        val contentWidth = maxOf(WIDTH, columns * COLUMN + (columns - 1) * GAP)
        val left = width / 2 - contentWidth / 2
        val rows = (step.options.size + columns - 1) / columns

        val description = MultiLineTextWidget(left, 0, Component.translatable("proupdater.step.${step.id}.description"), font)
            .setMaxWidth(contentWidth)
        val optionsHeight = if (step.options.isEmpty()) 0 else 22 + rows * ROW
        val total = 11 + 16 + description.height + 8 + ROW + optionsHeight + 8 + 20
        var y = maxOf(6, (height - total) / 2)

        val progress = Component.translatable("proupdater.wizard.progress", (page + 1).toString(), pages.size.toString())
        addRenderableWidget(StringWidget(left, y, contentWidth, 9, progress.withStyle(ChatFormatting.GRAY), font))
        y += 11
        var name: Component = Component.translatable("proupdater.step.${step.id}").withStyle(ChatFormatting.BOLD)
        if (panel.isNew(step)) {
            name = name.copy().append(Component.translatable("proupdater.screen.new").withStyle(ChatFormatting.YELLOW))
        }
        addRenderableWidget(StringWidget(left, y, contentWidth, 9, name, font))
        y += 16
        description.setY(y)
        addRenderableWidget(description)
        y += description.height + 8

        addRenderableWidget(
            CycleButton.onOffBuilder(selected)
                .create(left, y, contentWidth, 20, Component.translatable("proupdater.wizard.apply")) { _, value ->
                    panel.selected[step.id] = value
                    listening = null
                    rebuildWidgets()
                },
        )
        y += ROW

        if (step.options.isNotEmpty()) {
            y += 4
            val hint = Component.translatable("proupdater.wizard.keysHint").withStyle(ChatFormatting.GRAY)
            addRenderableWidget(StringWidget(left, y, contentWidth, 9, hint, font))
            y += 18
            val columnWidth = (contentWidth - (columns - 1) * GAP) / columns
            step.options.forEachIndexed { index, option ->
                val x = left + (index / rows) * (columnWidth + GAP)
                val rowY = y + (index % rows) * ROW
                val label = Component.translatable("proupdater.step.${step.id}.option.${option.id}")
                when (option.kind) {
                    OptionKind.TOGGLE -> addRenderableWidget(
                        CycleButton.onOffBuilder(values[option.id] == "true")
                            .create(x, rowY, columnWidth, 20, label) { _, value -> values[option.id] = value.toString() }
                            .also { it.active = selected },
                    )
                    OptionKind.KEY -> {
                        val labelWidth = columnWidth - KEY_BUTTON - 4
                        addRenderableWidget(StringWidget(x, rowY + 6, labelWidth, 9, label, font))
                        addRenderableWidget(
                            Button.builder(keyText(step, option, values)) {
                                listening = option
                                rebuildWidgets()
                            }.bounds(x + labelWidth + 4, rowY, KEY_BUTTON, 20).build().also { it.active = selected },
                        )
                    }
                }
            }
            y += rows * ROW
        }
        y += 8

        val third = (contentWidth - 8) / 3
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK) { go(page - 1) }.bounds(left, y, third, 20).build())
        if (step.options.isNotEmpty()) {
            val reset = Button.builder(Component.translatable("proupdater.screen.resetDefaults")) {
                values.clear()
                values.putAll(step.defaultOptions())
                listening = null
                rebuildWidgets()
            }.bounds(left + third + 4, y, third, 20).build()
            reset.active = selected
            addRenderableWidget(reset)
        }
        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.wizard.next")) { go(page + 1) }
                .bounds(left + contentWidth - third, y, third, 20).build(),
        )
    }

    private fun initSummary() {
        val left = width / 2 - WIDTH / 2
        val lines = panel.steps.map { step ->
            val name = Component.translatable("proupdater.step.${step.id}")
            when {
                !panel.isAvailable(step) -> Component.translatable(
                    "proupdater.wizard.summary.unavailable", name, step.requiredMods.joinToString(),
                ).withStyle(ChatFormatting.DARK_GRAY)
                panel.selected[step.id] == true -> Component.translatable("proupdater.wizard.summary.on", name).withStyle(ChatFormatting.GREEN)
                else -> Component.translatable("proupdater.wizard.summary.off", name).withStyle(ChatFormatting.GRAY)
            }
        }
        val hint = MultiLineTextWidget(left, 0, Component.translatable("proupdater.wizard.summary.hint"), font).setMaxWidth(WIDTH)
        val total = 16 + lines.size * 12 + 8 + hint.height + 10 + 20 + 14
        var y = maxOf(6, (height - total) / 2)

        addRenderableWidget(
            StringWidget(left, y, WIDTH, 9, Component.translatable("proupdater.wizard.summary.title").withStyle(ChatFormatting.BOLD), font),
        )
        y += 16
        for (line in lines) {
            addRenderableWidget(StringWidget(left, y, WIDTH, 9, line, font))
            y += 12
        }
        y += 8
        hint.setY(y)
        addRenderableWidget(hint)
        y += hint.height + 10

        val half = (WIDTH - 4) / 2
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK) { go(page - 1) }.bounds(left, y, half, 20).build())
        val anything = pages.any { panel.selected[it.id] == true }
        val apply = Button.builder(Component.translatable("proupdater.wizard.finish")) {
            val custom = onApply
            if (custom != null) {
                custom()
            } else {
                panel.queueSelectedSteps()
                onClose()
            }
        }.bounds(left + half + 4, y, half, 20).build()
        apply.active = anything || onApply != null
        addRenderableWidget(apply)
        y += 24
        if (!anything && onApply == null) {
            addRenderableWidget(
                StringWidget(left, y, WIDTH, 9, Component.translatable("proupdater.wizard.nothing").withStyle(ChatFormatting.GRAY), font),
            )
        }
    }

    /** Back from the first page leaves the wizard; the choices stay until the main screen is closed. */
    private fun go(target: Int) {
        listening = null
        if (target < 0) {
            onClose()
            return
        }
        page = target.coerceAtMost(pages.size)
        rebuildWidgets()
    }

    private fun columnsFor(step: SetupStep): Int {
        val wanted = if (step.options.size > 8) 3 else 2
        val fits = ((width - 16 + GAP) / (COLUMN + GAP)).coerceAtLeast(1)
        return minOf(wanted, fits)
    }

    private fun keyText(step: SetupStep, option: StepOption, values: Map<String, String>): Component {
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
        val step = pages.getOrNull(page) ?: return super.keyPressed(event)
        val name = if (event.key() == ESCAPE) KeyNames.UNBOUND else InputConstants.getKey(event).name
        // Keys that end up as GLFW codes in a mod config must be plain keyboard keys.
        if (!option.glfwOnly || name == KeyNames.UNBOUND || KeyNames.glfwCode(name) != null) {
            panel.options.getValue(step.id)[option.id] = name
        }
        listening = null
        rebuildWidgets()
        return true
    }

    override fun onClose() = McCompat.setScreen(parent)

    private companion object {
        const val WIDTH = 310
        const val COLUMN = 140
        const val GAP = 8
        const val ROW = 24
        const val KEY_BUTTON = 64
        const val ESCAPE = 256
    }
}
