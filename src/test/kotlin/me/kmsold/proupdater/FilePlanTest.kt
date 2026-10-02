package me.kmsold.proupdater

import me.kmsold.proupdater.core.FileAction
import me.kmsold.proupdater.core.FileChanges
import me.kmsold.proupdater.core.FilePlan
import me.kmsold.proupdater.core.FileStrategy
import me.kmsold.proupdater.core.Hashes
import me.kmsold.proupdater.core.Manifest
import me.kmsold.proupdater.core.PlanKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FilePlanTest {

    private val manifest = Manifest().apply {
        version = "3.4.0"
        files = mapOf(
            "config/a.json" to "x",
            "options.txt" to "y",
            "manifest.json" to "meta",
            "../outside.txt" to "evil",
            "/etc/passwd" to "evil",
        )
    }

    @Test
    fun `meta files and paths outside the game folder are never planned`() {
        assertEquals(listOf("config/a.json", "options.txt"), FilePlan.plan(PlanKind.APPLY_ALL, manifest).map { it.path })
    }

    @Test
    fun `clean install replaces files but only adds missing options`() {
        assertEquals(
            listOf(FileAction("config/a.json", FileStrategy.REPLACE), FileAction("options.txt", FileStrategy.ADD_MISSING_OPTIONS)),
            FilePlan.plan(PlanKind.CLEAN_INSTALL, manifest),
        )
    }

    @Test
    fun `update keeps everything that exists and apply all replaces everything`() {
        assertEquals(setOf(FileStrategy.KEEP_EXISTING), FilePlan.plan(PlanKind.UPDATE, manifest).map { it.strategy }.toSet())
        assertEquals(setOf(FileStrategy.REPLACE), FilePlan.plan(PlanKind.APPLY_ALL, manifest).map { it.strategy }.toSet())
    }

    private fun source() = memory(
        "proupdater/defaults/config/a.json" to "pack a",
        "proupdater/defaults/config/new.json" to "pack new",
        "proupdater/defaults/options.txt" to "version:4790\ntoggleAttack:false\nfov:0.5\n",
        "config/a.json" to "player a",
        "options.txt" to "version:4790\nfov:1.0\n",
    )

    private val all = Manifest().apply {
        version = "3.4.0"
        files = mapOf("config/a.json" to "", "config/new.json" to "", "options.txt" to "")
    }

    @Test
    fun `update only adds files the player does not have`() {
        val files = FileChanges(source())
        val applied = FilePlan.execute(FilePlan.plan(PlanKind.UPDATE, all), files) {}
        assertEquals("player a", files.readText("config/a.json"))
        assertEquals("pack new", files.readText("config/new.json"))
        assertEquals("version:4790\nfov:1.0\n", files.readText("options.txt"))
        assertEquals(mapOf("config/new.json" to Hashes.sha1("pack new".toByteArray())), applied)
    }

    @Test
    fun `clean install keeps a synced options file and adds the pack keys it lacks`() {
        val files = FileChanges(source())
        FilePlan.execute(FilePlan.plan(PlanKind.CLEAN_INSTALL, all), files) {}
        assertEquals("pack a", files.readText("config/a.json"))
        assertEquals("version:4790\nfov:1.0\ntoggleAttack:false\n", files.readText("options.txt"))
    }

    @Test
    fun `apply all replaces every file`() {
        val files = FileChanges(source())
        FilePlan.execute(FilePlan.plan(PlanKind.APPLY_ALL, all), files) {}
        assertEquals("pack a", files.readText("config/a.json"))
        assertEquals("version:4790\ntoggleAttack:false\nfov:0.5\n", files.readText("options.txt"))
    }

    @Test
    fun `a manifest entry without a pack copy is skipped`() {
        val files = FileChanges(memory())
        val log = mutableListOf<String>()
        val applied = FilePlan.execute(listOf(FileAction("config/gone.json", FileStrategy.REPLACE)), files) { log += it }
        assertEquals(emptyMap<String, String>(), applied)
        assertNull(files.readText("config/gone.json"))
        assertFalse(log.isEmpty())
    }
}
