package me.kmsold.proupdater.steps

import me.kmsold.proupdater.core.FileChanges
import me.kmsold.proupdater.core.KeyNames

/** Something the player sees in chat after joining a world, as a translation key with arguments. */
data class Notice(val key: String, val args: List<String> = emptyList())

class StepContext(
    val files: FileChanges,
    val isModLoaded: (String) -> Boolean,
) {
    val notices = mutableListOf<Notice>()
    val log = mutableListOf<String>()

    fun notice(key: String, vararg args: String) {
        notices += Notice(key, args.toList())
    }

    fun log(message: String) {
        log += message
    }
}

enum class OptionKind { KEY, TOGGLE }

/**
 * One setting of a step. Values travel as strings: a Minecraft key name for [OptionKind.KEY],
 * `true`/`false` for [OptionKind.TOGGLE]. [asCode] keys end up as a number in a mod config (see
 * [KeyNames]), so only keyboard keys and mouse buttons with a known number are accepted.
 */
data class StepOption(val id: String, val kind: OptionKind, val default: String, val asCode: Boolean = false)

/**
 * One thing the mod can set up. [apply] works on file contents only, never on the running game,
 * because it runs before Minecraft and the other mods start.
 *
 * Adding a step is one class plus one line in [Steps.all], and its name and description in the
 * language files: `proupdater.step.<id>` and `proupdater.step.<id>.description`, options under
 * `proupdater.step.<id>.option.<option id>`.
 */
interface SetupStep {
    val id: String

    /** Raised when the step changes; a higher version is offered to players who already have the step. */
    val version: Int

    /** Mod ids that must all be loaded for the step to make sense. */
    val requiredMods: List<String>

    val options: List<StepOption> get() = emptyList()

    /** Every file [apply] may change, game-relative. A manual backup saves these. */
    val files: List<String>

    /** Part of a clean install and of "Apply pack settings". */
    val enabledByDefault: Boolean get() = true

    /**
     * Whether a newer version is pointed out to players after a pack update. Steps that would wipe
     * the player's own data are only ever run from the button.
     */
    val offeredOnUpdate: Boolean get() = true

    fun isAvailable(isModLoaded: (String) -> Boolean) = requiredMods.all(isModLoaded)

    fun defaultOptions(): Map<String, String> = options.associate { it.id to it.default }

    /** [options] with every missing or invalid value replaced by its default. */
    fun resolve(options: Map<String, String>): Map<String, String> = this.options.associate { option ->
        val value = options[option.id]
        val valid = when {
            value == null -> false
            option.kind == OptionKind.TOGGLE -> value == "true" || value == "false"
            option.asCode -> value == KeyNames.UNBOUND || KeyNames.code(value) != null
            else -> value.startsWith("key.")
        }
        option.id to if (valid) value!! else option.default
    }

    fun apply(context: StepContext, options: Map<String, String>)
}
