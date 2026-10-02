package me.kmsold.proupdater

import me.kmsold.proupdater.core.KeyNames
import me.kmsold.proupdater.steps.Steps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KeyNamesTest {

    @Test
    fun `the keys NoFrills uses in the pack map to their glfw codes`() {
        assertEquals(85, KeyNames.code("key.keyboard.u"))
        assertEquals(76, KeyNames.code("key.keyboard.l"))
        assertEquals(45, KeyNames.code("key.keyboard.minus"))
        assertEquals(96, KeyNames.code("key.keyboard.grave.accent"))
        assertEquals(269, KeyNames.code("key.keyboard.end"))
        assertEquals(48, KeyNames.code("key.keyboard.0"))
        assertEquals(290, KeyNames.code("key.keyboard.f1"))
        assertEquals("key.keyboard.u", KeyNames.nameOf(85))
    }

    @Test
    fun `mouse buttons are numbered like NoFrills stores them`() {
        assertEquals(0, KeyNames.code("key.mouse.left"))
        assertEquals(2, KeyNames.code("key.mouse.middle"))
        assertEquals(3, KeyNames.code("key.mouse.4"))
        assertEquals(4, KeyNames.code("key.mouse.5"))
        assertEquals("key.mouse.4", KeyNames.nameOf(3))
    }

    @Test
    fun `unbound and unknown names have no number`() {
        assertNull(KeyNames.code(KeyNames.UNBOUND))
        assertNull(KeyNames.code("key.mouse.9"))
        assertNull(KeyNames.code("key.keyboard.nonsense"))
    }

    @Test
    fun `every step option has a valid default`() {
        for (step in Steps.all) {
            assertEquals(step.defaultOptions(), step.resolve(step.defaultOptions()), step.id)
        }
    }

    @Test
    fun `every step and option has a name in the language file`() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/assets/proupdater/lang/en_us.json"))
        val keys = stream.bufferedReader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject.keySet() }
        for (step in Steps.all) {
            for (key in listOf("proupdater.step.${step.id}", "proupdater.step.${step.id}.description") +
                step.options.map { "proupdater.step.${step.id}.option.${it.id}" }) {
                assert(key in keys) { "missing $key" }
            }
        }
    }
}
