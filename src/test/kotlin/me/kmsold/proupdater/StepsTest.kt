package me.kmsold.proupdater

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kmsold.proupdater.core.InstallState
import me.kmsold.proupdater.core.JsonFile
import me.kmsold.proupdater.core.KeyNames
import me.kmsold.proupdater.steps.NoFrillsCommandKeybindsStep
import me.kmsold.proupdater.steps.NoFrillsSlotBindingsStep
import me.kmsold.proupdater.steps.NoFrillsViewmodelStep
import me.kmsold.proupdater.steps.ResourcePacksStep
import me.kmsold.proupdater.steps.SkyblockerLoadoutStep
import me.kmsold.proupdater.steps.Steps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StepsTest {

    private val noFrills = """
        {
          "slotBinding": {
            "lines": true,
            "enabled": true,
            "keybind": 73,
            "data": {
              "hotbar1": {
                "last": 27,
                "binds": [
                  27
                ]
              },
              "hotbar4": {
                "last": 0,
                "binds": []
              }
            },
            "lineWidth": 2.0
          },
          "loadoutKeybinds": {
            "enabled": true
          },
          "customKeybinds": {
            "enabled": false,
            "allowInGui": false,
            "data": {
              "binds": [
                {
                  "name": "pearl from bags",
                  "key": 269,
                  "command": "/nf getPearls",
                  "enabled": true,
                  "allowInGui": false,
                  "modifier": "Any",
                  "islandFilter": ""
                },
                {
                  "name": "my wardrobe",
                  "key": 66,
                  "command": "/wardrobe",
                  "enabled": false,
                  "allowInGui": true,
                  "modifier": "Shift",
                  "islandFilter": "Hub"
                }
              ]
            }
          }
        }
    """.trimIndent()

    private fun json(text: String?): JsonObject = JsonParser.parseString(text).asJsonObject

    private fun binds(text: String?) = json(text).getAsJsonObject("customKeybinds").getAsJsonObject("data")
        .getAsJsonArray("binds").map { it.asJsonObject }

    @Test
    fun `command keybinds are added or updated by command without duplicates`() {
        val ctx = context(memory("config/NoFrills/Configuration.json" to noFrills), "nofrills")
        val step = NoFrillsCommandKeybindsStep()
        step.apply(ctx, step.defaultOptions())
        step.apply(ctx, step.defaultOptions())

        val result = ctx.text("config/NoFrills/Configuration.json")
        assertTrue(json(result).getAsJsonObject("customKeybinds").get("enabled").asBoolean)
        val binds = binds(result)
        assertEquals(listOf("/nf getPearls", "/wardrobe", "/eq", "/trades"), binds.map { it.get("command").asString })

        val wardrobe = binds[1]
        assertEquals(76, wardrobe.get("key").asInt)
        assertTrue(wardrobe.get("enabled").asBoolean)
        assertFalse(wardrobe.get("allowInGui").asBoolean)
        assertEquals("my wardrobe", wardrobe.get("name").asString)
        assertEquals("Shift", wardrobe.get("modifier").asString)
        assertEquals("Hub", wardrobe.get("islandFilter").asString)

        assertEquals(85, binds[2].get("key").asInt)
        assertEquals(45, binds[3].get("key").asInt)
        assertEquals(269, binds[0].get("key").asInt)
    }

    @Test
    fun `chosen keys are written as glfw codes and unbinding switches the bind off`() {
        val ctx = context(memory("config/NoFrills/Configuration.json" to noFrills), "nofrills")
        NoFrillsCommandKeybindsStep().apply(
            ctx,
            mapOf("equipment" to "key.keyboard.grave.accent", "wardrobe" to KeyNames.UNBOUND, "trades" to "key.mouse.left"),
        )
        val binds = binds(ctx.text("config/NoFrills/Configuration.json")).associateBy { it.get("command").asString }
        assertEquals(96, binds.getValue("/eq").get("key").asInt)
        assertFalse(binds.getValue("/wardrobe").get("enabled").asBoolean)
        // A mouse button has no GLFW key code, so the default is used.
        assertEquals(45, binds.getValue("/trades").get("key").asInt)
    }

    @Test
    fun `slot bindings are emptied and everything else stays`() {
        val ctx = context(memory("config/NoFrills/Configuration.json" to noFrills), "nofrills")
        NoFrillsSlotBindingsStep().apply(ctx, emptyMap())
        val result = json(ctx.text("config/NoFrills/Configuration.json"))
        val slotBinding = result.getAsJsonObject("slotBinding")
        val data = slotBinding.getAsJsonObject("data")
        for (slot in 1..9) {
            val hotbar = data.getAsJsonObject("hotbar$slot")
            assertEquals(0, hotbar.get("last").asInt)
            assertEquals(0, hotbar.getAsJsonArray("binds").size())
        }
        assertTrue(slotBinding.get("enabled").asBoolean)
        assertEquals(73, slotBinding.get("keybind").asInt)
        assertEquals(2, binds(ctx.text("config/NoFrills/Configuration.json")).size)
    }

    @Test
    fun `slot binding cleanup is never offered after an update`() {
        val state = InstallState().apply { packVersion = "3.4.0" }
        assertFalse(Steps.isNew(NoFrillsSlotBindingsStep(), state, null))
        assertTrue(Steps.isNew(SkyblockerLoadoutStep(), state, null))
    }

    @Test
    fun `new resource packs in the pack list are offered`() {
        val state = InstallState().apply {
            packVersion = "3.4.0"
            steps["resource_packs"] = 1
            resourcePacks = mutableListOf("vanilla", "file/FurSky.zip")
        }
        assertFalse(Steps.isNew(ResourcePacksStep(), state, listOf("vanilla", "file/FurSky.zip")))
        assertTrue(Steps.isNew(ResourcePacksStep(), state, listOf("vanilla", "file/FurSky.zip", "file/New.zip")))
        assertFalse(Steps.isNew(ResourcePacksStep(), InstallState(), listOf("file/New.zip")), "nothing is new before the first install")
    }

    private val skyblocker = """
        {
          "version": 12,
          "general": {
            "enableTips": true,
            "scale": 1.0,
            "text": "a = b & c"
          },
          "helpers": {
            "enableWardrobeHelper": false,
            "other": 3
          }
        }
    """.trimIndent()

    private val odin = """
        [
          {
            "name": "Loadout Keybinds",
            "enabled": true,
            "settings": {
              "Loadout 1": "key.keyboard.1"
            }
          },
          {
            "name": "Room Clear",
            "enabled": true,
            "settings": {}
          }
        ]
    """.trimIndent()

    private fun loadoutFiles() = memory(
        "config/skyblocker.json" to skyblocker,
        "config/odin/odin-config.json" to odin,
        "config/NoFrills/Configuration.json" to noFrills,
        "config/skyhanni/config.json" to """{"inventory": {"customLoadout": {"keybinds": {"slotKeybindsToggle": true, "slot1": 49}}}}""",
        "options.txt" to """
            version:4790
            key_key.skyblocker.loadout.01:key.keyboard.unknown
            key_key.iq.loadouts-slot-1:key.keyboard.1
            key_key.iq.wardrobe-slot-1:key.keyboard.1
            key_firmament.config.wardrobe-keybinds.slot-1:key.keyboard.1
            key_key.other:key.keyboard.1
        """.trimIndent() + "\n",
    )

    @Test
    fun `skyblocker loadout turns the helper on without reformatting the file`() {
        val ctx = context(loadoutFiles(), "skyblocker")
        val step = SkyblockerLoadoutStep()
        step.apply(ctx, step.defaultOptions())
        assertEquals(skyblocker.replace("\"enableWardrobeHelper\": false", "\"enableWardrobeHelper\": true"), ctx.text("config/skyblocker.json"))
    }

    @Test
    fun `skyblocker loadout keys are set and the other loadout keys are turned off`() {
        val ctx = context(loadoutFiles(), "skyblocker", "iqaddons")
        val step = SkyblockerLoadoutStep()
        step.apply(ctx, step.defaultOptions() + ("slot_12" to "key.keyboard.f12"))

        val options = ctx.text("options.txt")!!.lines()
        assertTrue("key_key.skyblocker.loadout.01:key.keyboard.1" in options)
        assertTrue("key_key.skyblocker.loadout.10:key.keyboard.0" in options)
        assertTrue("key_key.skyblocker.loadout.11:key.keyboard.minus" in options)
        assertTrue("key_key.skyblocker.loadout.12:key.keyboard.f12" in options)
        assertTrue("key_key.iq.loadouts-slot-1:key.keyboard.unknown" in options)
        assertTrue("key_key.iq.loadouts-slot-12:key.keyboard.unknown" in options, "IQ is installed, so missing slots are unbound too")
        assertTrue("key_key.iq.wardrobe-slot-1:key.keyboard.unknown" in options)
        assertTrue("key_firmament.config.wardrobe-keybinds.slot-1:key.keyboard.unknown" in options)
        assertFalse(options.any { it.startsWith("key_firmament.config.wardrobe-keybinds.slot-2") })
        assertTrue("key_key.other:key.keyboard.1" in options)

        assertFalse(json(ctx.text("config/NoFrills/Configuration.json")).getAsJsonObject("loadoutKeybinds").get("enabled").asBoolean)
        assertFalse(
            json(ctx.text("config/skyhanni/config.json")).getAsJsonObject("inventory").getAsJsonObject("customLoadout")
                .getAsJsonObject("keybinds").get("slotKeybindsToggle").asBoolean,
        )
        val odinModules = JsonParser.parseString(ctx.text("config/odin/odin-config.json")).asJsonArray.map { it.asJsonObject }
        assertFalse(odinModules[0].get("enabled").asBoolean)
        assertTrue(odinModules[1].get("enabled").asBoolean)
    }

    @Test
    fun `the IQ wardrobe can be left bound, and IQ keys are only added when IQ is installed`() {
        val ctx = context(loadoutFiles(), "skyblocker")
        val step = SkyblockerLoadoutStep()
        step.apply(ctx, step.defaultOptions() + (SkyblockerLoadoutStep.UNBIND_IQ_WARDROBE to "false"))
        val options = ctx.text("options.txt")!!.lines()
        assertTrue("key_key.iq.wardrobe-slot-1:key.keyboard.1" in options)
        assertTrue("key_key.iq.loadouts-slot-1:key.keyboard.unknown" in options)
        assertFalse(options.any { it.startsWith("key_key.iq.loadouts-slot-2:") })
    }

    @Test
    fun `configs that are already right are not rewritten`() {
        val source = memory("config/odin/odin-config.json" to odin.replace("\"enabled\": true,\n    \"settings\": {\n      \"Loadout", "\"enabled\": false,\n    \"settings\": {\n      \"Loadout"))
        val ctx = context(source, "skyblocker")
        SkyblockerLoadoutStep().apply(ctx, emptyMap())
        assertTrue(ctx.files.changed().isEmpty())
        assertNull(ctx.text("options.txt"))
    }

    @Test
    fun `the loadout step binds the key that opens Loadouts through NoFrills`() {
        val ctx = context(loadoutFiles(), "skyblocker", "nofrills")
        val step = SkyblockerLoadoutStep()
        step.apply(ctx, step.defaultOptions())
        val binds = binds(ctx.text("config/NoFrills/Configuration.json")).associateBy { it.get("command").asString }
        assertEquals(96, binds.getValue("/ld").get("key").asInt)
        assertEquals("loadout", binds.getValue("/ld").get("name").asString)
        assertFalse(binds.getValue("/ld").get("allowInGui").asBoolean)
        assertTrue(json(ctx.text("config/NoFrills/Configuration.json")).getAsJsonObject("customKeybinds").get("enabled").asBoolean)

        // The author's own bind is matched by command, whatever it is called.
        step.apply(ctx, step.defaultOptions() + ("open_loadouts" to "key.keyboard.o"))
        val again = binds(ctx.text("config/NoFrills/Configuration.json")).filter { it.get("command").asString == "/ld" }
        assertEquals(listOf(79), again.map { it.get("key").asInt })
    }

    @Test
    fun `without NoFrills the Loadouts key is skipped quietly`() {
        val ctx = context(memory("config/skyblocker.json" to skyblocker), "skyblocker")
        SkyblockerLoadoutStep().apply(ctx, emptyMap())
        assertTrue(ctx.notices.isEmpty())
        assertNull(ctx.text("config/NoFrills/Configuration.json"))
    }

    @Test
    fun `every step only changes the files it declares`() {
        for (step in Steps.all) {
            val ctx = context(
                loadoutFiles().also {
                    it.files["proupdater/defaults/resourcepacks.json"] = "[\"vanilla\"]".toByteArray()
                },
                "skyblocker", "nofrills", "iqaddons", "skyhanni", "odin", "firmament",
            )
            step.apply(ctx, step.defaultOptions())
            val undeclared = ctx.files.changed().keys - step.files.toSet()
            assertTrue(undeclared.isEmpty(), "${step.id} changed $undeclared")
        }
    }

    @Test
    fun `json escaping follows the original file`() {
        val escaped = JsonFile.parse("{\n  \"a\": \"x \\u003d y\"\n}")!!
        escaped.setBoolean(listOf("b"), true)
        assertEquals("{\n  \"a\": \"x \\u003d y\",\n  \"b\": true\n}", escaped.toText())

        val plain = JsonFile.parse("{\n  \"a\": \"x = y\"\n}\n")!!
        plain.setBoolean(listOf("b"), true)
        assertEquals("{\n  \"a\": \"x = y\",\n  \"b\": true\n}\n", plain.toText())
    }

    private val playerViewmodel = """
        {
          "slotBinding": {
            "enabled": true
          },
          "viewmodel": {
            "enabled": false,
            "offsetX": 0.0,
            "scaleX": 1.0,
            "noHaste": false
          }
        }
    """.trimIndent() + "\n"

    private val packViewmodel = """
        {
          "viewmodel": {
            "enabled": true,
            "offsetX": 0.15,
            "scaleX": 0.7,
            "noHaste": true,
            "speed": 10
          }
        }
    """.trimIndent() + "\n"

    private fun viewmodelFiles(withPackCopy: Boolean) = memory(
        "config/NoFrills/Configuration.json" to playerViewmodel,
        *(if (withPackCopy) arrayOf("proupdater/defaults/config/NoFrills/Configuration.json" to packViewmodel) else emptyArray()),
    )

    @Test
    fun `viewmodel takes the pack values and turns the feature on`() {
        val ctx = context(viewmodelFiles(withPackCopy = true), "nofrills")
        NoFrillsViewmodelStep().apply(ctx, NoFrillsViewmodelStep().defaultOptions())
        val result = json(ctx.text("config/NoFrills/Configuration.json"))
        val viewmodel = result.getAsJsonObject("viewmodel")
        assertTrue(viewmodel.get("enabled").asBoolean)
        assertEquals(0.15, viewmodel.get("offsetX").asDouble)
        assertEquals(0.7, viewmodel.get("scaleX").asDouble)
        assertTrue(viewmodel.get("noHaste").asBoolean)
        assertEquals(10, viewmodel.get("speed").asInt)
        assertTrue(result.getAsJsonObject("slotBinding").get("enabled").asBoolean)
    }

    @Test
    fun `viewmodel without the pack values only switches it on`() {
        val ctx = context(viewmodelFiles(withPackCopy = true), "nofrills")
        NoFrillsViewmodelStep().apply(ctx, mapOf(NoFrillsViewmodelStep.PACK_VALUES to "false"))
        val viewmodel = json(ctx.text("config/NoFrills/Configuration.json")).getAsJsonObject("viewmodel")
        assertTrue(viewmodel.get("enabled").asBoolean)
        assertEquals(0.0, viewmodel.get("offsetX").asDouble)
        assertEquals(1.0, viewmodel.get("scaleX").asDouble)
        assertNull(viewmodel.get("speed"))
    }

    @Test
    fun `viewmodel outside the pack only switches it on`() {
        val ctx = context(viewmodelFiles(withPackCopy = false), "nofrills")
        NoFrillsViewmodelStep().apply(ctx, NoFrillsViewmodelStep().defaultOptions())
        val viewmodel = json(ctx.text("config/NoFrills/Configuration.json")).getAsJsonObject("viewmodel")
        assertTrue(viewmodel.get("enabled").asBoolean)
        assertEquals(1.0, viewmodel.get("scaleX").asDouble)
        assertTrue(ctx.log.any { "only switching it on" in it })
    }

    @Test
    fun `viewmodel that is already right is not rewritten`() {
        val files = memory(
            "config/NoFrills/Configuration.json" to packViewmodel,
            "proupdater/defaults/config/NoFrills/Configuration.json" to packViewmodel,
        )
        val ctx = context(files, "nofrills")
        NoFrillsViewmodelStep().apply(ctx, NoFrillsViewmodelStep().defaultOptions())
        assertTrue(ctx.files.changed().isEmpty())
    }
}
