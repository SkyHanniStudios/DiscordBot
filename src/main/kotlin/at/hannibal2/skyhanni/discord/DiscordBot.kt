package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.handlers.Commands
import at.hannibal2.skyhanni.discord.handlers.Commands.commands
import at.hannibal2.skyhanni.discord.handlers.Database
import at.hannibal2.skyhanni.discord.util.EventUtil.logAction
import at.hannibal2.skyhanni.discord.util.EventUtil.reply
import at.hannibal2.skyhanni.discord.util.Util.hasPermissions
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.LoggerFactory
import java.util.Scanner

class DiscordBot(private val config: BotConfig) : ListenerAdapter() {
	val logger = LoggerFactory.getLogger(DiscordBot::class.java)
    override fun onMessageReceived(event: MessageReceivedEvent) {
        // fix working on other servers
        if (event.guild.id != config.allowedServerId || event.author.isBot) return

        val message = event.message.contentRaw.trim()
        if (!message.startsWith("!")) return
        val args = message.split(" ", limit = 3)

        if (commands.contains(args[0].substring(1))) {
            if (event.channel.id != config.botCommandChannelId) return

            val command = commands[args[0].substring(1)] ?: return
            if (command.permissions == "Staff" && !event.hasPermissions()) return

            command.execute(event, args.subList(1, args.size))
        } else {
            var keyword = message.substring(1)
            var deleting = false
            if (keyword.endsWith(" -d")) {
                keyword = keyword.dropLast(3)
                deleting = true
            }
            val response = Database.getResponse(keyword)
            if (response != null) {
                val channelName = event.channel.name
                event.message.referencedMessage?.let {
                    event.logAction("used reply keyword '$keyword' in channel '$channelName'")
                    event.message.delete().queue()
                    it.reply(response).queue()
                } ?: run {
                    if (deleting) {
                        event.logAction("used keyword with delete '$keyword' in channel '$channelName'")
                        event.message.delete().queue {
                            event.channel.sendMessage(response).queue()
                        }
                    } else {
                        event.reply(response)
                    }
                }
                return
            } else {
                event.reply("Unknown command \uD83E\uDD7A Type `!help` for help.")
            }
        }
    }
}

fun main() {
    val config = ConfigLoader.load("src/main/kotlin/at/hannibal2/skyhanni/discord/config.json")
    val token = config.token

    val jda = with(JDABuilder.createDefault(token)) {
        addEventListeners(DiscordBot(config))
        enableIntents(GatewayIntent.MESSAGE_CONTENT)
    }.build()
    jda.awaitReady()

    Commands.registerCommands()

    fun sendMessageToBotChannel(message: String) {
        jda.getTextChannelById(config.botCommandChannelId)?.sendMessage(message)?.queue()
    }

    sendMessageToBotChannel("I'm awake \uD83D\uDE42")

    Thread {
        val scanner = Scanner(System.`in`)
        while (scanner.hasNextLine()) {
            val input = scanner.nextLine().trim().lowercase()
            if (input in listOf("close", "stop", "exit", "end")) {
                sendMessageToBotChannel("Manually shutting down \uD83D\uDC4B")
                jda.shutdown()
                break
            }
        }
    }.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        sendMessageToBotChannel("I am the shutdown hook and I say bye \uD83D\uDC4B")
    })
}
