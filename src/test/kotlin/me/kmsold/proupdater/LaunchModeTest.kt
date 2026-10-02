package me.kmsold.proupdater

import me.kmsold.proupdater.core.InstallState
import me.kmsold.proupdater.core.LaunchMode
import me.kmsold.proupdater.core.Manifest
import me.kmsold.proupdater.core.Versions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LaunchModeTest {

    private fun manifest(version: String) = Manifest().apply {
        this.version = version
        files = mapOf("config/skyblocker.json" to "a", "config/NoFrills/Configuration.json" to "b", "options.txt" to "c")
    }

    private fun state(version: String?) = InstallState().apply { packVersion = version }

    @Test
    fun `no manifest means the mod runs outside the pack`() {
        assertEquals(LaunchMode.NO_MANIFEST, LaunchMode.detect(null, null) { true })
        assertEquals(LaunchMode.NO_MANIFEST, LaunchMode.detect(state("3.4.0"), null) { true })
    }

    @Test
    fun `no state and no pack config is a clean install`() {
        assertEquals(LaunchMode.CLEAN_INSTALL, LaunchMode.detect(null, manifest("3.4.0")) { false })
    }

    @Test
    fun `an options file alone does not stop a clean install`() {
        // Modrinth App keeps options.txt and may write a synced one before the first start.
        assertEquals(LaunchMode.CLEAN_INSTALL, LaunchMode.detect(null, manifest("3.4.0")) { it == "options.txt" })
    }

    @Test
    fun `no state but pack configs present is a player from the old layout`() {
        assertEquals(LaunchMode.LEGACY_MIGRATION, LaunchMode.detect(null, manifest("3.4.0")) { it == "config/skyblocker.json" })
    }

    @Test
    fun `a state without a pack version is judged by the files like no state`() {
        assertEquals(LaunchMode.CLEAN_INSTALL, LaunchMode.detect(state(null), manifest("3.4.0")) { false })
    }

    @Test
    fun `a newer manifest is an update, never a clean install`() {
        assertEquals(LaunchMode.UPDATE, LaunchMode.detect(state("3.4.0"), manifest("3.5.0")) { false })
    }

    @Test
    fun `the same version does nothing`() {
        assertEquals(LaunchMode.UP_TO_DATE, LaunchMode.detect(state("3.4.0"), manifest("3.4.0")) { true })
    }

    @Test
    fun `a downgrade only adds missing files`() {
        assertEquals(LaunchMode.UPDATE, LaunchMode.detect(state("3.5.0"), manifest("3.4.0")) { true })
    }

    @Test
    fun `versions compare by number`() {
        assertTrue(Versions.compare("3.10.0", "3.9.9") > 0)
        assertEquals(0, Versions.compare("3.4", "3.4.0"))
        assertEquals(0, Versions.compare("v3.4.0", "3.4.0+mc26.1"))
        assertTrue(Versions.compare("3.4.0", "3.4.1") < 0)
    }
}
