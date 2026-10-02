package me.kmsold.proupdater

import me.kmsold.proupdater.compat.McCompat
import me.kmsold.proupdater.screen.ProUpdaterScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ProUpdater : ClientModInitializer {

    const val MOD_ID = "proupdater"

    val logger: Logger = LoggerFactory.getLogger("PRO-Updater")

    /** The chat screen closes after the command runs, so the screen is opened on the next tick. */
    private var openScreen = false
    private var noticesDue = false

    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal(MOD_ID).executes {
                    openScreen = true
                    1
                },
            )
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> noticesDue = true }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (openScreen) {
                openScreen = false
                McCompat.setScreen(ProUpdaterScreen(null))
            }
            if (noticesDue && client.player != null) {
                noticesDue = false
                showNotices()
            }
        }
    }

    /** Once per game start, on the first world or server joined. */
    private fun showNotices() {
        if (LastRun.failed) {
            LastRun.failed = false
            chat(Component.translatable("proupdater.notice.failed"))
        }
        val report = LastRun.report ?: return
        LastRun.report = null
        for (notice in report.notices) {
            val args = notice.args.map { arg ->
                if (arg.startsWith("$MOD_ID.")) Component.translatable(arg) else Component.literal(arg)
            }
            chat(Component.translatable(notice.key, *args.toTypedArray()))
        }
    }

    fun chat(message: Component) {
        val prefix = Component.literal("[PRO-Updater] ").withStyle(ChatFormatting.GOLD)
        Minecraft.getInstance().player?.sendSystemMessage(prefix.append(message.copy().withStyle(ChatFormatting.YELLOW)))
    }
}
