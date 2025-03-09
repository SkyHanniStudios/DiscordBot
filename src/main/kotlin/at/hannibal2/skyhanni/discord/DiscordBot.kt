package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.sendMessageToBotChannel
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import java.util.Scanner

object DiscordBot : ListenerAdapter() {

    lateinit var config: BotConfig
        private set

    lateinit var jda: JDA
        private set

    lateinit var commands: CommandListener
        private set

    fun setJda(jda: JDA) {
        this.jda = jda
    }

    fun setConfig(config: BotConfig): DiscordBot {
        this.config = config
        this.commands = CommandListener(config)
        return this
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        commands.onMessage(this, event)
    }
}

const val PLEADING_FACE = "🥺"

fun main() {
    val config = ConfigLoader.load("config.json")
    val token = config.token

    val jda = JDABuilder.createDefault(token).addEventListeners(DiscordBot.setConfig(config))
        .enableIntents(GatewayIntent.MESSAGE_CONTENT).build()
    jda.awaitReady()

    DiscordBot.setJda(jda)

    sendMessageToBotChannel("I'm awake 🙂")

    Thread {
        val scanner = Scanner(System.`in`)
        while (scanner.hasNextLine()) {
            when (scanner.nextLine().trim().lowercase()) {
                "close", "stop", "exit", "end" -> {
                    sendMessageToBotChannel("Manually shutting down 👋")
                    jda.shutdown()
                    break
                }
            }
        }
    }.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        sendMessageToBotChannel("I am the shutdown hook and I say bye 👋")
    })
}
