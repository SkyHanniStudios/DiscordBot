package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.messageSend
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import java.util.Scanner

class DiscordBot(private val jda: JDA, val config: BotConfig) {
    var manualShutdown = false

    fun sendMessageToBotChannel(message: String) {
        jda.getTextChannelById(config.botCommandChannelId)?.messageSend(message)
    }

    fun shutdown() {
        sendMessageToBotChannel("Manually shutting down \uD83D\uDC4B")
        manualShutdown = true
        jda.shutdown()
    }
}

class MessageListener(val sendMessage: (MessageReceivedEvent) -> Unit) : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        sendMessage(event)
    }
}

const val PLEADING_FACE = "\uD83E\uDD7A"

fun main() {
    val bot = startBot()

    bot.sendMessageToBotChannel("I'm awake \uD83D\uDE42")

    Thread {
        val scanner = Scanner(System.`in`)
        while (scanner.hasNextLine()) {
            when (scanner.nextLine().trim().lowercase()) {
                "close", "stop", "exit", "end" -> {
                    bot.shutdown()
                    break
                }
            }
        }
    }.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        if (!bot.manualShutdown) {
            bot.sendMessageToBotChannel("I am the shutdown hook and I say bye \uD83D\uDC4B")
        }
    })
}

private fun startBot(): DiscordBot {
    val config = ConfigLoader.load("config.json")
    val token = config.token

    val jda = JDABuilder.createDefault(token).also { builder ->
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT)
    }.build()

    val bot = DiscordBot(jda, config)
    val commands = CommandListener(bot)
    jda.awaitReady()
    jda.addEventListener(MessageListener { commands.onMessage(bot, it) })
    return bot
}
