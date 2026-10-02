package me.kmsold.proupdater.steps

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.kmsold.proupdater.core.EditResult
import me.kmsold.proupdater.core.GamePaths
import me.kmsold.proupdater.core.JsonFile
import me.kmsold.proupdater.core.KeyNames
import me.kmsold.proupdater.core.editJson

private const val NOFRILLS = "nofrills"
private const val CONFIG = NoFrillsKeybinds.CONFIG

private fun StepContext.reportEdit(result: EditResult, what: String) {
    if (result == EditResult.MISSING || result == EditResult.UNREADABLE) {
        log("$CONFIG is ${result.name.lowercase()}, $what skipped")
        notice("proupdater.notice.configMissing", CONFIG)
    }
}

/**
 * NoFrills custom keybinds that run a command (`customKeybinds.data.binds` in
 * `config/NoFrills/Configuration.json`, keys as numbers, see [KeyNames]). Shared by every step that
 * binds a command.
 *
 * Binds are matched by command, so the player's own binds stay and nothing is added twice.
 * `allowInGui` is always false: the command keys never fire inside a menu, where the same keys
 * may switch loadouts.
 */
object NoFrillsKeybinds {

    const val CONFIG = "config/NoFrills/Configuration.json"

    /** The command written for a new bind, the commands that count as the same bind, the default key. */
    data class Command(val option: String, val name: String, val command: String, val aliases: Set<String>, val defaultKey: String)

    /** Binds each command to its Minecraft key name; an unbound key switches the bind off. */
    fun bind(context: StepContext, keys: List<Pair<Command, String>>, what: String) {
        val result = context.files.editJson(CONFIG) { json ->
            if (!json.setBoolean(listOf("customKeybinds", "enabled"), true)) return@editJson
            val data = json.objectAt("customKeybinds", "data", create = true) ?: return@editJson
            val binds = data.get("binds") as? JsonArray ?: JsonArray().also {
                data.add("binds", it)
                json.markChanged()
            }
            for ((command, key) in keys) {
                if (upsert(binds, command, key)) json.markChanged()
            }
        }
        context.reportEdit(result, what)
    }

    /** Returns true when [binds] changed. */
    private fun upsert(binds: JsonArray, command: Command, keyName: String): Boolean {
        val code = KeyNames.code(keyName)
        val existing = binds.filterIsInstance<JsonObject>().firstOrNull { bind ->
            val text = bind.get("command")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lowercase()
            text != null && text in command.aliases
        }
        val bind = existing ?: JsonObject().also {
            if (code == null) return false
            it.addProperty("name", command.name)
            it.addProperty("key", code)
            it.addProperty("command", command.command)
            it.addProperty("enabled", true)
            it.addProperty("allowInGui", false)
            it.addProperty("modifier", "Any")
            it.addProperty("islandFilter", "")
            binds.add(it)
            return true
        }
        val before = bind.deepCopy()
        if (code == null) {
            // Unbound on the screen: the bind is kept, just switched off.
            bind.addProperty("enabled", false)
        } else {
            bind.addProperty("key", code)
            bind.addProperty("enabled", true)
        }
        bind.addProperty("allowInGui", false)
        return bind != before
    }
}

/**
 * `/eq`, `/wd` and `/trades` on keys through NoFrills custom keybinds. Minus is also Skyblocker's
 * loadout slot 11, which only works inside the "Loadouts" window, so the two never meet.
 */
class NoFrillsCommandKeybindsStep : SetupStep {

    override val id = "nofrills_command_keybinds"
    override val version = 1
    override val requiredMods = listOf(NOFRILLS)
    override val files = listOf(CONFIG)

    override val options = COMMANDS.map { StepOption(it.option, OptionKind.KEY, it.defaultKey, asCode = true) }

    override fun apply(context: StepContext, options: Map<String, String>) {
        val values = resolve(options)
        NoFrillsKeybinds.bind(context, COMMANDS.map { it to values.getValue(it.option) }, "the command keybinds were")
    }

    companion object {
        val COMMANDS = listOf(
            NoFrillsKeybinds.Command("equipment", "equipment", "/eq", setOf("/eq", "/equipment"), "key.keyboard.u"),
            NoFrillsKeybinds.Command("wardrobe", "wardrobe", "/wd", setOf("/wd", "/wardrobe"), "key.keyboard.l"),
            NoFrillsKeybinds.Command("trades", "trades", "/trades", setOf("/trades"), "key.keyboard.minus"),
        )
    }
}

/**
 * Empties NoFrills slot bindings (`slotBinding.data.hotbar1` to `hotbar9`). The pack author's own
 * bindings travel in the pack otherwise. The feature and its key stay as they are.
 *
 * Runs on a clean install and from the button only - never on its own after an update, or players
 * would lose the bindings they made themselves.
 */
class NoFrillsSlotBindingsStep : SetupStep {

    override val id = "nofrills_clear_slot_bindings"
    override val version = 1
    override val requiredMods = listOf(NOFRILLS)
    override val files = listOf(CONFIG)
    override val offeredOnUpdate = false

    override fun apply(context: StepContext, options: Map<String, String>) {
        val result = context.files.editJson(CONFIG) { json ->
            val data = json.objectAt("slotBinding", "data", create = true) ?: return@editJson
            for (slot in 1..9) {
                val current = data.get("hotbar$slot")
                val cleared = ((current as? JsonObject)?.deepCopy() ?: JsonObject()).apply {
                    addProperty("last", 0)
                    add("binds", JsonArray())
                }
                if (cleared != current) {
                    data.add("hotbar$slot", cleared)
                    json.markChanged()
                }
            }
        }
        context.reportEdit(result, "clearing the slot bindings was")
    }
}

/**
 * Turns on NoFrills Viewmodel (`viewmodel` in `config/NoFrills/Configuration.json`): where the held
 * item sits, how big it is and how it swings.
 *
 * With [PACK_VALUES] on, the values come from the pack's own copy of that file in `defaults/`, so
 * they follow whatever the pack ships. With it off, or when the pack copy is missing (the mod runs
 * outside the pack), only the switch is flipped and the player's own values stay.
 */
class NoFrillsViewmodelStep : SetupStep {

    override val id = "nofrills_viewmodel"
    override val version = 1
    override val requiredMods = listOf(NOFRILLS)
    override val files = listOf(CONFIG)
    override val options = listOf(StepOption(PACK_VALUES, OptionKind.TOGGLE, "true"))

    override fun apply(context: StepContext, options: Map<String, String>) {
        val packValues = if (resolve(options).getValue(PACK_VALUES) == "true") packViewmodel(context) else null
        val result = context.files.editJson(CONFIG) { json ->
            val viewmodel = json.objectAt("viewmodel", create = true) ?: return@editJson
            packValues?.entrySet()?.forEach { (key, value) ->
                if (viewmodel.get(key) != value) {
                    viewmodel.add(key, value.deepCopy())
                    json.markChanged()
                }
            }
            json.setBoolean(listOf("viewmodel", "enabled"), true)
        }
        context.reportEdit(result, "turning on the viewmodel was")
    }

    private fun packViewmodel(context: StepContext): JsonObject? {
        val copy = context.files.readText(GamePaths.defaultsPath(CONFIG))?.let(JsonFile::parse)
        val values = (copy?.root as? JsonObject)?.get("viewmodel") as? JsonObject
        if (values == null) context.log("No viewmodel in the pack copy of $CONFIG, only switching it on")
        return values
    }

    companion object {
        const val PACK_VALUES = "pack_values"
    }
}
