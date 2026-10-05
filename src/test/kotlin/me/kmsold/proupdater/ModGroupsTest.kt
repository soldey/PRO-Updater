package me.kmsold.proupdater

import me.kmsold.proupdater.core.ModGroups
import me.kmsold.proupdater.core.ModInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModGroupsTest {

    /** Ids and names as the mods in Skyblock PRO declare them. */
    private val mods = listOf(
        ModInfo("sodium", "Sodium"),
        ModInfo("sodium-extra", "Sodium Extra"),
        ModInfo("nofrills", "NoFrills"),
        ModInfo("iqaddons", "IQ Addons"),
        ModInfo("cwb", "Cubes Without Borders"),
        ModInfo("firmament", "Firmament"),
        ModInfo("skyblocker", "Skyblocker"),
        ModInfo("skyblock-api", "SkyBlock API"),
        ModInfo("fabric-api", "Fabric API"),
        ModInfo("fabric-language-kotlin", "Fabric Language Kotlin"),
        ModInfo("chat_heads", "Chat Heads"),
    )

    private fun owner(path: String) = ModGroups.owner(path, mods).first

    @Test
    fun `files and folders named after the mod id belong to it`() {
        assertEquals("nofrills", owner("config/NoFrills/Configuration.json"))
        assertEquals("skyblocker", owner("config/skyblocker.json"))
        assertEquals("skyblocker", owner("config/skyblocker/hud_widgets.json"))
        assertEquals("skyblock-api", owner("config/skyblockapi/data.json"))
        assertEquals("chat_heads", owner("config/chat_heads.json5"))
    }

    @Test
    fun `a file that starts with a mod id goes to the longest one`() {
        assertEquals("sodium", owner("config/sodium-options.json"))
        assertEquals("sodium", owner("config/sodium-mixins.properties"))
        assertEquals("sodium-extra", owner("config/sodium-extra.properties"))
        assertEquals("sodium-extra", owner("config/sodium-extra-options.json"))
        assertEquals("firmament", owner("config/firmament-legacy-config-1771010805234/config.json"))
    }

    @Test
    fun `names match too, and a short folder name matches the only mod it starts`() {
        assertEquals("cwb", owner("config/cubes-without-borders.json"))
        assertEquals("cwb", owner("config/cwb.json"))
        assertEquals("iqaddons", owner("config/iq/data.json"))
        assertEquals("iqaddons", owner("config/iqaddons.jsonc"))
    }

    @Test
    fun `what matches nothing or too much keeps its own name`() {
        assertEquals("file:fabric" to "fabric", ModGroups.owner("config/fabric/indigo-renderer.properties", mods))
        assertEquals("file:yacl" to "yacl", ModGroups.owner("config/yacl.json5", mods))
    }

    @Test
    fun `options come first as Minecraft, the rest by name`() {
        val groups = ModGroups.group(
            listOf("config/sodium-options.json", "options.txt", "config/NoFrills/Configuration.json", "config/sodium-mixins.properties", "config/yacl.json5"),
            mods,
        )
        assertEquals(listOf("minecraft", "nofrills", "sodium", "file:yacl"), groups.map { it.key })
        assertEquals(listOf("config/sodium-mixins.properties", "config/sodium-options.json"), groups[2].paths)
    }
}
