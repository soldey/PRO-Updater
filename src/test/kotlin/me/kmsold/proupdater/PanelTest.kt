package me.kmsold.proupdater

import me.kmsold.proupdater.core.GamePaths
import me.kmsold.proupdater.core.InstallState
import me.kmsold.proupdater.core.ModelJson
import me.kmsold.proupdater.core.Panel
import me.kmsold.proupdater.core.PendingActions
import me.kmsold.proupdater.core.PendingStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class PanelTest {

    @TempDir
    lateinit var game: Path

    private val allMods = { _: String -> true }

    private fun write(path: String, text: String) {
        game.resolve(path).also { it.parent.createDirectories() }.writeText(text)
    }

    private fun on(panel: Panel) = panel.selected.filterValues { it }.keys

    @Test
    fun `steps never applied here start switched on, except clearing slot bindings`() {
        assertEquals(
            setOf("skyblocker_loadout", "nofrills_command_keybinds", "nofrills_viewmodel", "resource_packs", "attack_hold"),
            on(Panel(game, allMods)),
        )
    }

    @Test
    fun `steps already applied start switched off`() {
        val state = InstallState().apply {
            packVersion = "3.4.0"
            listOf("skyblocker_loadout", "nofrills_command_keybinds", "nofrills_clear_slot_bindings", "nofrills_viewmodel", "resource_packs", "attack_hold")
                .forEach { steps[it] = 1 }
        }
        write(GamePaths.STATE, ModelJson.write(state))
        assertEquals(emptySet<String>(), on(Panel(game, allMods)))
    }

    @Test
    fun `steps for missing mods start switched off`() {
        assertFalse(Panel(game) { it != "skyblocker" }.selected.getValue("skyblocker_loadout"))
    }

    @Test
    fun `a queue decides exactly what is switched on, with its options`() {
        write(
            GamePaths.PENDING,
            ModelJson.write(
                PendingActions().apply {
                    steps = mutableListOf(PendingStep.of("nofrills_clear_slot_bindings", emptyMap()), PendingStep.of("skyblocker_loadout", mapOf("slot_01" to "key.keyboard.f1")))
                },
            ),
        )
        val panel = Panel(game, allMods)
        assertEquals(setOf("nofrills_clear_slot_bindings", "skyblocker_loadout"), on(panel))
        assertEquals("key.keyboard.f1", panel.options.getValue("skyblocker_loadout")["slot_01"])
    }

    @Test
    fun `queueing nothing removes the queue file`() {
        val panel = Panel(game, allMods)
        panel.queueSelectedSteps()
        assert(game.resolve(GamePaths.PENDING).exists())
        panel.selected.replaceAll { _, _ -> false }
        panel.queueSelectedSteps()
        assertFalse(game.resolve(GamePaths.PENDING).exists())
    }
}
