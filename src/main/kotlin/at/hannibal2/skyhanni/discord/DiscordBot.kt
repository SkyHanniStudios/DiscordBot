package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.messageSend
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.LoggerFactory
import java.util.Scanner

object DiscordBot : ListenerAdapter() {
    val logger = LoggerFactory.getLogger(this::class.java)

    lateinit var config: BotConfig
        private set

    lateinit var jda: JDA
        private set

    lateinit var commands: CommandListener
        private set

    var manualShutdown = false

    fun setJda(jda: JDA) {
        this.jda = jda
    }

    fun setConfig(config: BotConfig): DiscordBot {
        this.config = config
        this.commands = CommandListener(config)
        return this
    }

    fun shutdown() {
        sendMessageToBotChannel("Manually shutting down \uD83D\uDC4B")
        manualShutdown = true
        jda.shutdown()
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        sendMessage(event)
    }
}

const val PLEADING_FACE = "🥺"

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
            bot.sendMessageToBotChannel("I am the shutdown hook and I say bye 👋")
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
    val commands = CommandListener(bot)
    jda.awaitReady()
    jda.addEventListener(MessageListener { commands.onMessage(bot, it) })
    return bot
}
