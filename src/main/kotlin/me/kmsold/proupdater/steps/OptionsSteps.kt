package me.kmsold.proupdater.steps

import me.kmsold.proupdater.core.EditResult
import me.kmsold.proupdater.core.GamePaths
import me.kmsold.proupdater.core.ModelJson
import me.kmsold.proupdater.core.editOptions

/**
 * Turns on the pack's resource packs in `options.txt` before Minecraft reads it, so they are on in
 * the main menu without a resource reload. The order comes from `defaults/resourcepacks.json`,
 * bottom first; packs the player enabled stay on and stay above the pack's.
 *
 * `incompatibleResourcePacks` is left alone: Minecraft only uses it for packs that are really
 * incompatible, and drops such a pack from the list unless the player confirmed it there.
 */
class ResourcePacksStep : SetupStep {

    override val id = "resource_packs"
    override val version = 1
    override val requiredMods = emptyList<String>()

    override fun apply(context: StepContext, options: Map<String, String>) {
        val wanted = packList(context) ?: return
        val available = wanted.filter { pack ->
            val file = fileOf(pack) ?: return@filter true
            context.files.exists("resourcepacks/$file").also { found ->
                if (!found) {
                    context.log("Resource pack $file is not in resourcepacks/, left out")
                    context.notice("proupdater.notice.resourcePackMissing", file)
                }
            }
        }
        val result = context.files.editOptions { file -> file.resourcePacks = order(file.resourcePacks, available) }
        if (result == EditResult.MISSING) context.log("No options.txt, the resource packs were not turned on")
    }

    companion object {
        const val VANILLA = "vanilla"

        /** The pack list from `defaults/resourcepacks.json`, or null without one. */
        fun packList(context: StepContext): List<String>? =
            context.files.readText(GamePaths.RESOURCE_PACKS)?.let(ModelJson::resourcePacks)

        /** `file/Name.zip` lives in `resourcepacks/Name.zip`; built-in packs have no file. */
        fun fileOf(pack: String): String? = pack.removePrefix("file/").takeIf { pack.startsWith("file/") && it.isNotEmpty() }

        /**
         * `vanilla` at the bottom, then [ours] in their order, then everything else the player had,
         * in the player's order.
         */
        fun order(current: List<String>, ours: List<String>): List<String> {
            val hasVanilla = VANILLA in current || VANILLA in ours
            val rest = current.filter { it != VANILLA && it !in ours }
            return (if (hasVanilla) listOf(VANILLA) else emptyList()) + ours.filter { it != VANILLA }.distinct() + rest
        }
    }
}

/** Attack/Destroy on hold instead of toggle (`toggleAttack:false`), without touching anything else. */
class AttackHoldStep : SetupStep {

    override val id = "attack_hold"
    override val version = 1
    override val requiredMods = emptyList<String>()

    override fun apply(context: StepContext, options: Map<String, String>) {
        val result = context.files.editOptions { it["toggleAttack"] = "false" }
        if (result == EditResult.MISSING) context.log("No options.txt, Attack/Destroy was not set to hold")
    }
}
