package me.kmsold.proupdater.steps

import me.kmsold.proupdater.core.InstallState

/** Every setup step, in the order they run and appear on the screen. A new step is one line here. */
object Steps {

    val all: List<SetupStep> = listOf(
        SkyblockerLoadoutStep(),
        NoFrillsCommandKeybindsStep(),
        NoFrillsSlotBindingsStep(),
        NoFrillsViewmodelStep(),
        ResourcePacksStep(),
        AttackHoldStep(),
    )

    fun byId(id: String): SetupStep? = all.firstOrNull { it.id == id }

    /**
     * Whether the screen should point the step out: a newer version than the player has seen, or,
     * for the resource packs, packs in `resourcepacks.json` the player has not been given yet.
     */
    fun isNew(step: SetupStep, state: InstallState?, packList: List<String>?): Boolean {
        if (!step.offeredOnUpdate) return false
        if (state?.packVersion == null) return false
        if ((state.steps[step.id] ?: 0) < step.version) return true
        if (step is ResourcePacksStep && packList != null) return packList.any { it !in state.resourcePacks }
        return false
    }

    /** Applies [step] and remembers it in [state]. */
    fun run(step: SetupStep, options: Map<String, String>, context: StepContext, state: InstallState) {
        val resolved = step.resolve(options)
        step.apply(context, resolved)
        state.steps[step.id] = step.version
        if (resolved.isNotEmpty()) state.stepOptions[step.id] = resolved
        if (step is ResourcePacksStep) {
            ResourcePacksStep.packList(context)?.let { packs ->
                state.resourcePacks = (state.resourcePacks + packs).distinct().toMutableList()
            }
        }
    }
}
