package me.kmsold.proupdater

import me.kmsold.proupdater.core.KeyNames
import me.kmsold.proupdater.steps.Steps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KeyNamesTest {

    @Test
    fun `the keys NoFrills uses in the pack map to their glfw codes`() {
        assertEquals(85, KeyNames.glfwCode("key.keyboard.u"))
        assertEquals(76, KeyNames.glfwCode("key.keyboard.l"))
        assertEquals(45, KeyNames.glfwCode("key.keyboard.minus"))
        assertEquals(96, KeyNames.glfwCode("key.keyboard.grave.accent"))
        assertEquals(269, KeyNames.glfwCode("key.keyboard.end"))
        assertEquals(48, KeyNames.glfwCode("key.keyboard.0"))
        assertEquals(290, KeyNames.glfwCode("key.keyboard.f1"))
        assertEquals("key.keyboard.u", KeyNames.nameOf(85))
    }

    @Test
    fun `mouse buttons and unbound have no glfw key code`() {
        assertNull(KeyNames.glfwCode("key.mouse.left"))
        assertNull(KeyNames.glfwCode(KeyNames.UNBOUND))
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
