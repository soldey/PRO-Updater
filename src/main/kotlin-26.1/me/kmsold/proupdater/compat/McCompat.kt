package me.kmsold.proupdater.compat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * The client calls that moved between Minecraft versions. 26.2 split `Gui` into a screen half
 * and a `Hud` half, so the current screen is reached differently there; every other API the mod
 * uses is the same, which is why only this file exists per version.
 */
object McCompat {

    val currentScreen: Screen? get() = Minecraft.getInstance().screen

    fun setScreen(screen: Screen?) = Minecraft.getInstance().setScreen(screen)
}
