package me.kmsold.proupdater.steps

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import me.kmsold.proupdater.core.EditResult
import me.kmsold.proupdater.core.KeyNames
import me.kmsold.proupdater.core.editJson
import me.kmsold.proupdater.core.editOptions

/**
 * Loadout switching on keys inside the "Loadouts" window, done by Skyblocker. Every other mod
 * that switches loadouts on slot keys is turned off, or both would fire in the same window.
 *
 * Skyblocker 6.10.4: `helpers.enableWardrobeHelper` in `config/skyblocker.json` turns the feature
 * on (and the hotbar keys in "Armor Sets" / "Equipment Sets"), the keys are
 * `key_key.skyblocker.loadout.01` to `.12` in `options.txt`.
 *
 * Built so another provider can be picked later: the loadout mods are listed in [Provider], one is
 * switched on and the rest off. Only Skyblocker can be picked so far.
 */
class SkyblockerLoadoutStep : SetupStep {

    override val id = "skyblocker_loadout"
    override val version = 1
    override val requiredMods = listOf(SKYBLOCKER)

    override val options: List<StepOption> =
        SLOT_DEFAULTS.mapIndexed { i, key -> StepOption(slotOption(i + 1), OptionKind.KEY, "key.keyboard.$key") } +
            StepOption(UNBIND_IQ_WARDROBE, OptionKind.TOGGLE, "true")

    enum class Provider { SKYBLOCKER, IQ, SKYHANNI, ODIN, NOFRILLS, FIRMAMENT }

    override fun apply(context: StepContext, options: Map<String, String>) {
        val values = resolve(options)
        enable(Provider.SKYBLOCKER, context, values)
        Provider.entries.filter { it != Provider.SKYBLOCKER }.forEach { disable(it, context, values) }
    }

    private fun enable(provider: Provider, context: StepContext, values: Map<String, String>) {
        check(provider == Provider.SKYBLOCKER) { "Only Skyblocker can provide loadout keys so far" }
        val result = context.files.editJson(SKYBLOCKER_CONFIG) { it.setBoolean(listOf("helpers", "enableWardrobeHelper"), true) }
        if (result == EditResult.MISSING || result == EditResult.UNREADABLE) {
            context.log("$SKYBLOCKER_CONFIG is ${result.name.lowercase()}, the loadout helper was not switched on")
        }
        context.files.editOptions { file ->
            for (slot in 1..SLOTS) file[skyblockerKey(slot)] = values.getValue(slotOption(slot))
        }.also { if (it == EditResult.MISSING) context.log("No options.txt, the Skyblocker loadout keys were not set") }
    }

    private fun disable(provider: Provider, context: StepContext, values: Map<String, String>) {
        val files = context.files
        when (provider) {
            Provider.SKYBLOCKER -> Unit
            Provider.IQ -> files.editOptions { file ->
                val keys = (1..SLOTS).map { "key_key.iq.loadouts-slot-$it" } +
                    if (values[UNBIND_IQ_WARDROBE] == "true") (1..9).map { "key_key.iq.wardrobe-slot-$it" } else emptyList()
                // IQ binds its slots by default, so the keys are written even when the line is not there yet.
                val iqInstalled = context.isModLoaded(IQ)
                for (key in keys) if (iqInstalled || key in file) file[key] = KeyNames.UNBOUND
            }
            Provider.SKYHANNI -> files.editJson(SKYHANNI_CONFIG) {
                it.setBoolean(listOf("inventory", "customLoadout", "keybinds", "slotKeybindsToggle"), false)
            }
            Provider.ODIN -> files.editJson(ODIN_CONFIG) { json ->
                val modules = json.root.takeIf { it.isJsonArray }?.asJsonArray ?: return@editJson
                modules.filterIsInstance<JsonObject>()
                    .filter { it.get("name")?.takeIf { n -> n.isJsonPrimitive }?.asString == ODIN_MODULE }
                    .filter { it.get("enabled") != JsonPrimitive(false) }
                    .forEach {
                        it.addProperty("enabled", false)
                        json.markChanged()
                    }
            }
            Provider.NOFRILLS -> files.editJson(NOFRILLS_CONFIG) {
                it.setBoolean(listOf("loadoutKeybinds", "enabled"), false)
            }
            Provider.FIRMAMENT -> files.editOptions { file ->
                for (slot in 1..9) {
                    val key = "key_firmament.config.wardrobe-keybinds.slot-$slot"
                    if (key in file) file[key] = KeyNames.UNBOUND
                }
            }
        }
    }

    companion object {
        const val SLOTS = 12
        const val UNBIND_IQ_WARDROBE = "unbind_iq_wardrobe"
        const val SKYBLOCKER = "skyblocker"
        const val IQ = "iqaddons"
        const val SKYBLOCKER_CONFIG = "config/skyblocker.json"
        const val SKYHANNI_CONFIG = "config/skyhanni/config.json"
        const val ODIN_CONFIG = "config/odin/odin-config.json"
        const val ODIN_MODULE = "Loadout Keybinds"
        const val NOFRILLS_CONFIG = "config/NoFrills/Configuration.json"

        /** 01-09 on 1-9, 10 on 0, 11 on minus, 12 on equals, like the number row. */
        private val SLOT_DEFAULTS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "minus", "equal")

        fun slotOption(slot: Int) = "slot_%02d".format(slot)

        fun skyblockerKey(slot: Int) = "key_key.skyblocker.loadout.%02d".format(slot)
    }
}
