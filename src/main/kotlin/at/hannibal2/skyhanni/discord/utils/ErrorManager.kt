package at.hannibal2.skyhanni.discord.utils

import at.hannibal2.skyhanni.discord.PING_HANNIBAL
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.sendMessageToBotChannel

object ErrorManager {

    fun Exception.handleError(vararg customMessages: String) {
        printStackTrace()
        val message = buildList {
            add("Caught SkyHanniBot error!")
            addAll(customMessages)
            add("```")
            add(formattedStackTrace())
            add("```")
            add("Obligatory ping to $PING_HANNIBAL $PLEADING_FACE")

        }.joinToString("\n")
        try {
            sendMessageToBotChannel(message)
        } catch (e: Throwable) {
            println("can not send error message to bot channel!")
            println("message:")
            println(" \n$message\n ")
            printStackTrace()
            e.printStackTrace()
        }
    }

    private fun Throwable.formattedStackTrace(): String = getCustomStackTrace(fullStackTrace = true).joinToString("\n")

    private fun Throwable.getCustomStackTrace(
        fullStackTrace: Boolean,
        parent: List<String> = emptyList(),
    ): List<String> = buildList {
        this.add("Caused by ${javaClass.name}: $message")

        for (traceElement in stackTrace) {
            val text = "\tat $traceElement"
            if (!fullStackTrace && text in parent) {
                break
            }
            var visualText = text
            if (!fullStackTrace) {
                for ((from, to) in replaceEntirely) {
                    if (visualText.contains(from)) {
                        visualText = to
                        break
                    }
                }
                for ((from, to) in replace) {
                    visualText = visualText.replace(from, to)
                }
            }
            if (!fullStackTrace && breakAfter.any<String> { text.contains(it) }) {
                this.add(visualText)
                break
            }
            if (ignored.any { text.contains(it) }) continue
            this.add(visualText)
        }

        if (this === cause) {
            this.add("<Infinite recurring causes>")
            return@buildList
        }

        cause?.let<Throwable, Unit> {
            addAll(it.getCustomStackTrace(fullStackTrace, this))
        }
    }

    private val replaceEntirely = mapOf(
        "at.hannibal2.skyhanni.api.event.EventListeners.createZeroParameterConsumer" to "<Skyhanni event post>",
        "at.hannibal2.skyhanni.api.event.EventListeners.createSingleParameterConsumer" to "<Skyhanni event post>",
    )

    private val replace = mapOf(
        "at.hannibal2.skyhanni." to "SH.",
        "io.moulberry.notenoughupdates." to "NEU.",
        "net.minecraft." to "MC.",
        "net.minecraftforge.fml." to "FML.",
        "knot//" to "",
        "java.base/" to "",
    )

    private val breakAfter = listOf(
        "at at.hannibal2.skyhanni.config.commands.Commands\$createCommand",
        "at net.minecraftforge.fml.common.eventhandler.EventBus.post",
        "at at.hannibal2.skyhanni.mixins.hooks.NetHandlerPlayClientHookKt.onSendPacket",
        "at net.minecraft.client.main.Main.main",
        "at.hannibal2.skyhanni.api.event.EventListeners.createZeroParameterConsumer",
        "at.hannibal2.skyhanni.api.event.EventListeners.createSingleParameterConsumer",
    )

    private val ignored = listOf(
        "at java.lang.Thread.run",
        "at java.util.concurrent.",
        "at java.lang.reflect.",
        "at net.minecraft.network.",
        "at net.minecraft.client.Minecraft.addScheduledTask(",
        "at net.minecraftforge.fml.common.network.handshake.",
        "at net.minecraftforge.fml.common.eventhandler.",
        "at net.fabricmc.devlaunchinjector.",
        "at io.netty.",
        "at com.google.gson.internal.",
        "at sun.reflect.",

        "at at.hannibal2.skyhanni.config.commands.SimpleCommand.",
        "at at.hannibal2.skyhanni.config.commands.Commands\$createCommand\$1.processCommand",
        "at at.hannibal2.skyhanni.test.command.ErrorManager.logError",
        "at at.hannibal2.skyhanni.test.command.ErrorManager.skyHanniError",
        "at at.hannibal2.skyhanni.api.event.SkyHanniEvent.post",
        "at at.hannibal2.skyhanni.api.event.EventHandler.post",
        "at net.minecraft.launchwrapper.",
    )
}