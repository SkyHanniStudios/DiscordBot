package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.sendMessageToBotChannel
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Scanner

lateinit var BOT: DiscordBot
    private set

class DiscordBot(val jda: JDA, val config: BotConfig) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    init {
        BOT = this
        CommandListener.init()
    }

    var manualShutdown = false

    fun shutdown() {
        sendMessageToBotChannel("Manually shutting down \uD83D\uDC4B")
        manualShutdown = true
        jda.shutdown()
    }
}

const val PLEADING_FACE = "🥺"
const val PARTY_FACE = "\uD83E\uDD73"
const val BIG_X = "❌"
const val CHECK_MARK = "✅"
const val PING_HANNIBAL = "<@239858538959077376>"
const val OPEN_PR_TAG = "1350893914768277624"

fun main() {
    val bot = startBot()

    sendMessageToBotChannel("I'm awake \uD83D\uDE42")

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
            sendMessageToBotChannel("I am the shutdown hook and I say bye 👋", instantly = true)
            // since we disable the JDA shutdown hook we need to call shutdown manually to make everything clean
            bot.shutdown()
        }
    })
}

private fun startBot(): DiscordBot {
    val config = ConfigLoader.load("config.json")
    val token = config.token

    val jda = JDABuilder.createDefault(token).also { builder ->
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT)
        builder.setEnableShutdownHook(false)
    }.build()

    val bot = DiscordBot(jda, config)
    jda.awaitReady()
    val messageListener = object : ListenerAdapter() {
        override fun onMessageReceived(event: MessageReceivedEvent) {
            CommandListener.onMessage(bot, event)
        }
    }
    jda.addEventListener(messageListener)
    return bot
}
