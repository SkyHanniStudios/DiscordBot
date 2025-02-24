package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent

class DiscordBot(private val config: BotConfig) : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        // fix working on other servers
        if (event.guild.id != config.allowedServerId) return

        val message = event.message.contentRaw.trim()
        val args = message.split(" ", limit = 3)

        if (event.author.isBot) return

        if (message.startsWith("!")) {
            val keyword = message.substring(1)
            val response = Database.getResponse(keyword)
            if (response != null) {
                event.channel.sendMessage(response).queue()
                return
            }
        }

        // checking that only staff can change tags
        if (event.channel.id != config.botCommandChannelId) return

        when {
            args[0] == "!add" && args.size == 3 -> {
                val keyword = args[1]
                val response = args[2]
                if (Database.listKeywords().contains(keyword.lowercase())) {
                    event.channel.sendMessage("❌ Already exists use `!edit` instead.").queue()
                    return
                }
                if (Database.addKeyword(keyword, response)) {
                    event.channel.sendMessage("✅ Keyword '$keyword' added!").queue()
                } else {
                    event.channel.sendMessage("❌ Failed to add keyword.").queue()
                }
                return
            }

            args[0] == "!edit" && args.size == 3 -> {
                val keyword = args[1]
                val response = args[2]
                if (!Database.listKeywords().contains(keyword.lowercase())) {
                    event.channel.sendMessage("❌ Keyword does not exist! Use `!add` instead.").queue()
                    return
                }
                if (Database.addKeyword(keyword, response)) {
                    event.channel.sendMessage("✅ Keyword '$keyword' edited!").queue()
                } else {
                    event.channel.sendMessage("❌ Failed to edit keyword.").queue()
                }
                return
            }

            args[0] == "!delete" && args.size == 2 -> {
                val keyword = args[1]
                if (Database.deleteKeyword(keyword)) {
                    event.channel.sendMessage("🗑️ Keyword '$keyword' deleted!").queue()
                } else {
                    event.channel.sendMessage("❌ Keyword '$keyword' not found.").queue()
                }
                return
            }

            message == "!list" -> {
                val keywords = Database.listKeywords().joinToString(", ")
                val response = if (keywords.isNotEmpty()) "📌 Keywords: $keywords" else "No keywords set."
                event.channel.sendMessage(response).queue()
                return
            }

            message == "!help" -> {
                val commands = listOf("add", "remove", "list", "edit")
                val response = "The bot currently supports those commands: ${commands.joinToString(", ")}"
                event.channel.sendMessage(response).queue()
                return
            }
        }

        if (message.startsWith("!")) {
            event.channel.sendMessage("Unknown command \uD83E\uDD7A Type `!help` for help.").queue()
        }
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

    Runtime.getRuntime().addShutdownHook(Thread {
        sendMessageToBotChannel("I'm tired, see you later \uD83D\uDE26")
    })
}