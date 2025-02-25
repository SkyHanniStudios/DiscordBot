package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Scanner

class DiscordBot(private val config: BotConfig) : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        // fix working on other servers
        if (event.guild.id != config.allowedServerId) return

        val message = event.message.contentRaw.trim()
        if (!message.startsWith("!")) return
        val args = message.split(" ", limit = 3)

        if (event.author.isBot) return

        fun logAction(action: String) {
            val author = event.author
            val name = author.name
            val effectiveName = author.effectiveName
            val globalName = author.globalName
            val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            println("$time $effectiveName ($name/$globalName) $action")
        }

        fun reply(message: String) {
            event.message.reply(message).queue()
        }

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
                logAction("used reply keyword '$keyword' in channel '$channelName'")
                event.message.delete().queue()
                it.reply(response).queue()
            } ?: run {
                if (deleting) {
                    logAction("used keyword with delete '$keyword' in channel '$channelName'")
                    event.message.delete().queue {
                        event.channel.sendMessage(response).queue()
                    }
                } else {
                    reply(response)
                }
            }
            return
        }

        if (message == "!taglist" || message == "!list") {
            val keywords = Database.listKeywords().joinToString(", ")
            reply(if (keywords.isNotEmpty()) "📌 Keywords: $keywords" else "No keywords set.")
            return
        }

        // checking that only staff can change tags
        if (event.channel.id != config.botCommandChannelId) return

        val member = event.member ?: return
        val allowedRoleIds = config.editPermissionRoleIds.values
        if (!member.roles.any { it.id in allowedRoleIds }) {
            reply("No perms \uD83E\uDD7A")
            // User doesn't have an allowed role; you can ignore or send a warning.
            return
        }

        when {
            args[0] == "!add" && args.size == 3 -> {
                val keyword = args[1]
                val response = args[2]
                if (Database.listKeywords().contains(keyword.lowercase())) {
                    reply("❌ Already exists use `!edit` instead.")
                    return
                }
                if (Database.addKeyword(keyword, response)) {
                    reply("✅ Keyword '$keyword' added!")
                    logAction("added keyword '$keyword'")
                    logAction("response: '$response'")
                } else {
                    reply("❌ Failed to add keyword.")
                }
                return
            }

            args[0] == "!edit" && args.size == 3 -> {
                val keyword = args[1]
                val response = args[2]
                val oldResponse = Database.getResponse(keyword)
                if (oldResponse == null) {
                    reply("❌ Keyword does not exist! Use `!add` instead.")
                    return
                }
                if (Database.addKeyword(keyword, response)) {
                    reply("✅ Keyword '$keyword' edited!")
                    logAction("edited keyword '$keyword'")
                    logAction("old response: '$oldResponse'")
                    logAction("new response: '$response'")
                } else {
                    reply("❌ Failed to edit keyword.")
                }
                return
            }

            (args[0] == "!delete" || args[0] == "!remove") && args.size == 2 -> {
                val keyword = args[1]
                val oldResponse = Database.getResponse(keyword)
                if (Database.deleteKeyword(keyword)) {
                    reply("🗑️ Keyword '$keyword' deleted!")
                    logAction("deleted keyword '$keyword'")
                    logAction("response was: '$oldResponse'")
                } else {
                    reply("❌ Keyword '$keyword' not found.")
                }
                return
            }

            message == "!help" -> {
                val commands = listOf("add", "delete", "list", "edit")
                reply("The bot currently supports these commands: ${commands.joinToString(", ", prefix = "!")}")
                return
            }
        }

        reply("Unknown command \uD83E\uDD7A Type `!help` for help.")
    }
}

fun main() {
    val config = ConfigLoader.load("config.json")
    val token = config.token

    val jda = with(JDABuilder.createDefault(token)) {
        addEventListeners(DiscordBot(config))
        enableIntents(GatewayIntent.MESSAGE_CONTENT)
    }.build()
    jda.awaitReady()

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
