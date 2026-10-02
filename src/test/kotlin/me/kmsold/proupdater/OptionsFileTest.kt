package me.kmsold.proupdater

import me.kmsold.proupdater.core.KeyNames
import me.kmsold.proupdater.core.OptionsFile
import me.kmsold.proupdater.steps.ResourcePacksStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OptionsFileTest {

    private val text = """
        version:4790
        toggleAttack:true
        resourcePacks:["vanilla","high_contrast"]
        incompatibleResourcePacks:[]
        key_key.skyblocker.loadout.01:key.keyboard.1
        some.unknown.mod:value:with:colons
    """.trimIndent() + "\n"

    @Test
    fun `untouched files come back unchanged`() {
        assertEquals(text, OptionsFile.parse(text).toText())
        assertEquals("a:1", OptionsFile.parse("a:1").toText())
    }

    @Test
    fun `values are split at the first colon`() {
        assertEquals("value:with:colons", OptionsFile.parse(text)["some.unknown.mod"])
        assertNull(OptionsFile.parse(text)["missing"])
    }

    @Test
    fun `set replaces in place and appends new keys at the end`() {
        val file = OptionsFile.parse(text)
        file["toggleAttack"] = "false"
        file["key_key.iq.loadouts-slot-1"] = KeyNames.UNBOUND
        val lines = file.toText().lines()
        assertEquals("toggleAttack:false", lines[1])
        assertEquals("some.unknown.mod:value:with:colons", lines[5])
        assertEquals("key_key.iq.loadouts-slot-1:key.keyboard.unknown", lines[6])
    }

    @Test
    fun `windows line endings are read`() {
        assertEquals("1", OptionsFile.parse("a:1\r\nb:2\r\n")["a"])
    }

    @Test
    fun `resource pack list is read and written like Minecraft does`() {
        val file = OptionsFile.parse(text)
        assertEquals(listOf("vanilla", "high_contrast"), file.resourcePacks)
        file.resourcePacks = listOf("vanilla", "file/FurSky Reborn.cats.zip")
        assertEquals("""["vanilla","file/FurSky Reborn.cats.zip"]""", file["resourcePacks"])
        assertEquals(emptyList<String>(), OptionsFile.parse("resourcePacks:broken")
            .resourcePacks)
    }

    @Test
    fun `pack packs go above vanilla and the player's packs stay above them`() {
        assertEquals(
            listOf("vanilla", "file/FurSky.zip", "file/Other.zip", "high_contrast", "file/Mine.zip"),
            ResourcePacksStep.order(
                current = listOf("vanilla", "high_contrast", "file/FurSky.zip", "file/Mine.zip"),
                ours = listOf("vanilla", "file/FurSky.zip", "file/Other.zip"),
            ),
        )
        assertEquals(listOf("vanilla", "file/FurSky.zip"), ResourcePacksStep.order(emptyList(), listOf("vanilla", "file/FurSky.zip")))
    }

    @Test
    fun `resource packs step turns on available packs and reports missing ones`() {
        val ctx = context(
            memory(
                "options.txt" to "version:4790\nresourcePacks:[\"vanilla\",\"high_contrast\"]\nincompatibleResourcePacks:[]\n",
                "proupdater/defaults/resourcepacks.json" to """["vanilla","file/FurSky Reborn.cats.zip","file/Gone.zip"]""",
                "resourcepacks/FurSky Reborn.cats.zip" to "zip",
            ),
        )
        ResourcePacksStep().apply(ctx, emptyMap())
        assertEquals(
            "version:4790\nresourcePacks:[\"vanilla\",\"file/FurSky Reborn.cats.zip\",\"high_contrast\"]\nincompatibleResourcePacks:[]\n",
            ctx.text("options.txt"),
        )
        assertEquals(listOf("Gone.zip"), ctx.notices.map { it.args.single() })
    }

    @Test
    fun `resource packs in a folder count as present`() {
        val ctx = context(
            memory(
                "options.txt" to "resourcePacks:[]\n",
                "proupdater/defaults/resourcepacks.json" to """["vanilla","file/Folder"]""",
                "resourcepacks/Folder/pack.mcmeta" to "{}",
            ),
        )
        ResourcePacksStep().apply(ctx, emptyMap())
        assertEquals("resourcePacks:[\"vanilla\",\"file/Folder\"]\n", ctx.text("options.txt"))
    }
}
