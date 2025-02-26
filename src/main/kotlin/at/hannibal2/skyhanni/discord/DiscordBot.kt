package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.messageSend
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import java.util.Scanner

class DiscordBot(val config: BotConfig, private val commands: Commands) : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        commands.onMessage(this, event)
    }
}

fun main() {
    val config = ConfigLoader.load("config.json")
    val token = config.token
    val commands = Commands(config)

    val jda = JDABuilder.createDefault(token)
        .addEventListeners(DiscordBot(config, commands))
        .enableIntents(GatewayIntent.MESSAGE_CONTENT)
        .build()
    jda.awaitReady()

    fun sendMessageToBotChannel(message: String) {
        jda.getTextChannelById(config.botCommandChannelId)?.messageSend(message)
    }

    sendMessageToBotChannel("I'm awake \uD83D\uDE42")

    Thread {
        val scanner = Scanner(System.`in`)
        while (scanner.hasNextLine()) {
            when (scanner.nextLine().trim().lowercase()) {
                "close", "stop", "exit", "end" -> {
                    sendMessageToBotChannel("Manually shutting down \uD83D\uDC4B")
                    jda.shutdown()
                    break
                }
            }
        }
    }.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        sendMessageToBotChannel("I am the shutdown hook and I say bye \uD83D\uDC4B")
    })
}
