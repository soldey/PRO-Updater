package me.kmsold.proupdater

import com.google.gson.JsonParser
import me.kmsold.proupdater.core.Backups
import me.kmsold.proupdater.core.GamePaths
import me.kmsold.proupdater.core.Hashes
import me.kmsold.proupdater.core.Installer
import me.kmsold.proupdater.core.LaunchMode
import me.kmsold.proupdater.core.ModelJson
import me.kmsold.proupdater.core.PendingActions
import me.kmsold.proupdater.core.PendingStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class InstallerTest {

    @TempDir
    lateinit var game: Path

    private val mods = setOf("skyblocker", "nofrills", "iqaddons")
    private var clock = ZonedDateTime.of(2026, 10, 2, 12, 0, 0, 0, ZoneOffset.UTC)

    private fun write(path: String, text: String) {
        val file = game.resolve(path)
        file.parent.createDirectories()
        file.writeText(text)
    }

    private fun read(path: String): String? = game.resolve(path).takeIf { it.exists() }?.readText()

    private val packNoFrills = """
        {
          "slotBinding": {
            "enabled": true,
            "data": {
              "hotbar1": {
                "last": 27,
                "binds": [
                  27
                ]
              }
            }
          },
          "loadoutKeybinds": {
            "enabled": false
          },
          "customKeybinds": {
            "enabled": true,
            "data": {
              "binds": []
            }
          }
        }
    """.trimIndent() + "\n"

    private val packOptions = "version:4790\ntoggleAttack:false\nresourcePacks:[\"vanilla\"]\nfov:0.5\n"

    private fun pack(version: String, extra: Map<String, String> = emptyMap()) {
        val files = mapOf(
            "config/skyblocker.json" to "{\n  \"helpers\": {\n    \"enableWardrobeHelper\": false\n  }\n}\n",
            "config/NoFrills/Configuration.json" to packNoFrills,
            "options.txt" to packOptions,
        ) + extra
        files.forEach { (path, text) -> write("proupdater/defaults/$path", text) }
        write(
            GamePaths.MANIFEST,
            ModelJson.write(mapOf("version" to version, "files" to files.mapValues { Hashes.sha1(it.value.toByteArray()) })),
        )
        write(GamePaths.RESOURCE_PACKS, """["vanilla","file/FurSky Reborn.cats.zip"]""")
    }

    private fun run() = Installer(game, { it in mods }) { clock }.run().also { clock = clock.plusMinutes(1) }

    private fun state() = ModelJson.state(read(GamePaths.STATE)!!)!!

    @Test
    fun `clean install lays everything out and runs the default steps`() {
        pack("3.4.0")
        write("resourcepacks/FurSky Reborn.cats.zip", "zip")
        val report = run()

        assertEquals(LaunchMode.CLEAN_INSTALL, report.mode)
        assertNull(report.backup, "nothing existed, so nothing needed a backup")
        val options = read("options.txt")!!.lines()
        assertTrue("key_key.skyblocker.loadout.01:key.keyboard.1" in options)
        assertTrue("resourcePacks:[\"vanilla\",\"file/FurSky Reborn.cats.zip\"]" in options)
        assertTrue("toggleAttack:false" in options)
        assertTrue(read("config/skyblocker.json")!!.contains("\"enableWardrobeHelper\": true"))
        val noFrills = JsonParser.parseString(read("config/NoFrills/Configuration.json")).asJsonObject
        assertEquals(0, noFrills.getAsJsonObject("slotBinding").getAsJsonObject("data").getAsJsonObject("hotbar1").get("last").asInt)
        assertEquals(3, noFrills.getAsJsonObject("customKeybinds").getAsJsonObject("data").getAsJsonArray("binds").size())

        val state = state()
        assertEquals("3.4.0", state.packVersion)
        assertNotNull(state.cleanInstallAt)
        assertEquals(3, state.files.size)
        assertEquals(setOf("skyblocker_loadout", "nofrills_command_keybinds", "nofrills_clear_slot_bindings", "resource_packs", "attack_hold"), state.steps.keys)
        assertEquals(listOf("vanilla", "file/FurSky Reborn.cats.zip"), state.resourcePacks)
    }

    @Test
    fun `a missing resource pack is left out and reported`() {
        pack("3.4.0")
        val report = run()
        assertTrue("resourcePacks:[\"vanilla\"]" in read("options.txt")!!.lines())
        assertTrue(report.notices.any { it.key == "proupdater.notice.resourcePackMissing" })
    }

    @Test
    fun `an update keeps the player's files and adds only new ones`() {
        pack("3.4.0")
        run()
        write("config/skyblocker.json", "player's own")
        write("options.txt", "version:4790\ntoggleAttack:true\n")
        val noFrillsBefore = read("config/NoFrills/Configuration.json")

        pack("3.5.0", mapOf("config/new-mod.json" to "{}\n"))
        val report = run()

        assertEquals(LaunchMode.UPDATE, report.mode)
        assertEquals("player's own", read("config/skyblocker.json"))
        assertEquals("version:4790\ntoggleAttack:true\n", read("options.txt"))
        assertEquals(noFrillsBefore, read("config/NoFrills/Configuration.json"))
        assertEquals("{}\n", read("config/new-mod.json"))
        assertNull(report.backup)
        assertEquals("3.5.0", state().packVersion)

        assertEquals(LaunchMode.UP_TO_DATE, run().mode)
    }

    @Test
    fun `a player from the old layout is not overwritten`() {
        pack("3.4.0")
        write("config/skyblocker.json", "player's own")
        write("options.txt", "version:4790\n")
        val report = run()
        assertEquals(LaunchMode.LEGACY_MIGRATION, report.mode)
        assertEquals("player's own", read("config/skyblocker.json"))
        assertEquals("version:4790\n", read("options.txt"))
        assertEquals(packNoFrills, read("config/NoFrills/Configuration.json"))
        assertNull(state().cleanInstallAt)
    }

    @Test
    fun `apply pack settings backs up first and restore brings the old files back`() {
        pack("3.4.0")
        run()
        write("config/skyblocker.json", "player's own")
        write("config/NoFrills/Configuration.json", "player's nofrills")

        write(GamePaths.PENDING, ModelJson.write(PendingActions().apply { applyPackDefaults = true }))
        val applied = run()
        assertNotNull(applied.backup)
        assertFalse(game.resolve(GamePaths.PENDING).exists(), "the queue is emptied after a run")
        assertTrue(read("config/skyblocker.json")!!.contains("\"enableWardrobeHelper\": true"))

        write(GamePaths.PENDING, ModelJson.write(PendingActions().apply { restoreBackup = applied.backup }))
        val restored = run()
        assertEquals("player's own", read("config/skyblocker.json"))
        assertEquals("player's nofrills", read("config/NoFrills/Configuration.json"))
        assertNotNull(restored.backup, "restoring is a mass write too")
        assertTrue(restored.notices.any { it.key == "proupdater.notice.restored" })
    }

    @Test
    fun `restoring a clean install backup removes the files it created`() {
        pack("3.4.0")
        write("options.txt", "version:4790\nfov:1.0\n")
        val install = run()
        val backup = install.backup
        assertNotNull(backup, "options.txt existed and was changed")
        assertTrue(game.resolve("config/skyblocker.json").exists())

        write(GamePaths.PENDING, ModelJson.write(PendingActions().apply { restoreBackup = backup }))
        run()
        assertFalse(game.resolve("config/skyblocker.json").exists())
        assertEquals("version:4790\nfov:1.0\n", read("options.txt"))
    }

    @Test
    fun `queued steps run with the player's options and missing mods are skipped`() {
        pack("3.4.0")
        run()
        write(
            GamePaths.PENDING,
            ModelJson.write(
                PendingActions().apply {
                    steps = mutableListOf(
                        PendingStep.of("skyblocker_loadout", mapOf("slot_01" to "key.keyboard.f1")),
                        PendingStep.of("unknown_step", emptyMap()),
                    )
                },
            ),
        )
        run()
        assertTrue("key_key.skyblocker.loadout.01:key.keyboard.f1" in read("options.txt")!!.lines())
        assertEquals("key.keyboard.f1", state().stepOptions.getValue("skyblocker_loadout")["slot_01"])
    }

    @Test
    fun `without a manifest only the queue is handled`() {
        write("options.txt", "version:4790\n")
        val report = run()
        assertEquals(LaunchMode.NO_MANIFEST, report.mode)
        assertEquals("version:4790\n", read("options.txt"))
        assertNull(read(GamePaths.STATE))
    }

    @Test
    fun `old backups are pruned`() {
        val dir = game.resolve(GamePaths.BACKUPS)
        repeat(Backups.KEEP + 3) { i -> Backups.write(dir, "2026-01-%02d_00-00-00_x.zip".format(i + 1), ByteArray(1)) }
        val left = Backups.list(dir)
        assertEquals(Backups.KEEP, left.size)
        assertEquals("2026-01-18_00-00-00_x.zip", left.first())
    }
}
