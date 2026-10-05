package me.kmsold.proupdater.screen

import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.core.ModGroup
import me.kmsold.proupdater.core.ModGroups
import me.kmsold.proupdater.core.ModInfo
import me.kmsold.proupdater.core.Panel
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/**
 * One checkbox per mod found in [backup], in two columns and as many pages as needed. The ticked
 * mods get their settings from the backup on the next start; everything else stays as it is.
 * [done] is where the screen goes after queueing: the main screen.
 */
class ModRestoreScreen(
    private val parent: Screen,
    private val done: Screen,
    private val panel: Panel,
    private val backup: String,
) : Screen(Component.translatable("proupdater.modRestore.title", backup.removeSuffix(".zip"))) {

    private val groups: List<ModGroup>? = panel.backupPaths(backup)?.let { paths ->
        val mods = FabricLoader.getInstance().allMods.map { ModInfo(it.metadata.id, it.metadata.name) }
        ModGroups.group(paths, mods)
    }

    /** Keys of the ticked groups, kept across pages and rebuilds; starts with what is queued from this backup. */
    private val ticked: MutableSet<String> = panel.pending.modRestore
        ?.takeIf { it.backup == backup }
        ?.let { queued -> groups.orEmpty().filter { it.paths.any(queued.paths::contains) }.map { it.key }.toMutableSet() }
        ?: mutableSetOf()

    private var page = 0

    override fun init() {
        val left = width / 2 - WIDTH / 2
        val hint = MultiLineTextWidget(left, 0, Component.translatable("proupdater.modRestore.hint"), font).setMaxWidth(WIDTH)
        val top = 6 + 12 + hint.height + 8
        val bottom = 3 * ROW + 8
        val rows = ((height - top - bottom) / ROW).coerceIn(2, 10)
        val perPage = rows * COLUMNS
        val all = groups.orEmpty()
        val pages = maxOf(1, (all.size + perPage - 1) / perPage)
        page = page.coerceIn(0, pages - 1)

        var y = 6
        addRenderableWidget(StringWidget(left, y, WIDTH, 9, title.copy().withStyle(ChatFormatting.BOLD), font))
        y += 12
        hint.setY(y)
        addRenderableWidget(hint)
        y = top

        when {
            groups == null -> addRenderableWidget(message("proupdater.modRestore.unreadable", left, y))
            groups.isEmpty() -> addRenderableWidget(message("proupdater.modRestore.empty", left, y))
            else -> {
                val columnWidth = (WIDTH - GAP) / COLUMNS
                all.drop(page * perPage).take(perPage).forEachIndexed { index, group ->
                    val x = left + (index / rows) * (columnWidth + GAP)
                    val rowY = y + (index % rows) * ROW
                    val label = Component.literal(group.name)
                        .append(Component.literal(" (${group.paths.size})").withStyle(ChatFormatting.GRAY))
                    addRenderableWidget(
                        Checkbox.builder(label, font)
                            .pos(x, rowY + 2)
                            .maxWidth(columnWidth)
                            .selected(group.key in ticked)
                            .tooltip(Tooltip.create(Component.literal(fileList(group))))
                            .onValueChange { _, value ->
                                if (value) ticked += group.key else ticked -= group.key
                                rebuildWidgets()
                            }
                            .build(),
                    )
                }
            }
        }

        val third = (WIDTH - 8) / 3
        var buttonsY = height - bottom + 8
        if (pages > 1) {
            addRenderableWidget(
                Button.builder(Component.literal("<")) { page--; rebuildWidgets() }
                    .bounds(left, buttonsY, third, 20).build().also { it.active = page > 0 },
            )
            val pageText = Component.translatable("proupdater.modRestore.page", (page + 1).toString(), pages.toString())
            addRenderableWidget(StringWidget(left + third + 4, buttonsY + 6, third, 9, pageText, font))
            addRenderableWidget(
                Button.builder(Component.literal(">")) { page++; rebuildWidgets() }
                    .bounds(left + 2 * (third + 4), buttonsY, third, 20).build().also { it.active = page < pages - 1 },
            )
        }
        buttonsY += ROW

        val half = (WIDTH - 4) / 2
        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.modRestore.all")) {
                ticked += all.map { it.key }
                rebuildWidgets()
            }.bounds(left, buttonsY, half, 20).build().also { it.active = all.isNotEmpty() },
        )
        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.modRestore.none")) {
                ticked.clear()
                rebuildWidgets()
            }.bounds(left + half + 4, buttonsY, half, 20).build().also { it.active = ticked.isNotEmpty() },
        )
        buttonsY += ROW

        val chosen = all.filter { it.key in ticked }
        addRenderableWidget(
            Button.builder(Component.translatable("proupdater.modRestore.apply", chosen.size.toString())) {
                panel.queueModRestore(backup, chosen)
                McCompat.setScreen(done)
            }.bounds(left, buttonsY, half, 20).build().also { it.active = chosen.isNotEmpty() },
        )
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK) { onClose() }.bounds(left + half + 4, buttonsY, half, 20).build())
    }

    private fun message(key: String, left: Int, y: Int) =
        StringWidget(left, y, WIDTH, 9, Component.translatable(key).withStyle(ChatFormatting.GRAY), font)

    /** The tooltip: the files that would come back, a dozen at most. */
    private fun fileList(group: ModGroup): String {
        val shown = group.paths.take(12)
        val more = group.paths.size - shown.size
        return (shown + if (more > 0) listOf("+$more") else emptyList()).joinToString("\n")
    }

    override fun onClose() = McCompat.setScreen(parent)

    private companion object {
        const val WIDTH = 320
        const val COLUMNS = 2
        const val GAP = 10
        const val ROW = 22
    }
}
