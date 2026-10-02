package me.kmsold.proupdater.compat

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.kmsold.proupdater.screen.ProUpdaterScreen

/** Only loaded by Fabric when Mod Menu is actually installed. */
class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> ProUpdaterScreen(parent) }
}
