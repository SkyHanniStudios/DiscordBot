package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.messageDeleteAndThen
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.sendMessageWithConsumer
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import kotlin.time.Duration.Companion.seconds

@Suppress("UNUSED_PARAMETER")
class TagCommands(private val config: BotConfig, commands: Commands) {
    val lastMessages = mutableMapOf<String, MutableList<Message>>()

    init {
        commands.add(Command("taglist", userCommand = true) { event, args -> event.listCommand(args) })
        commands.add(Command("tags", userCommand = true) { event, args -> event.listCommand(args) })

        commands.add(Command("tagedit") { event, args -> event.editCommand(args) })
        commands.add(Command("tagchange") { event, args -> event.editCommand(args) })

        commands.add(Command("tagadd") { event, args -> event.addCommand(args) })
        commands.add(Command("tagdelete") { event, args -> event.deleteCommand(args) })
        commands.add(Command("tagremove") { event, args -> event.deleteCommand(args) })

        commands.add(Command("undo", userCommand = true) { event, args -> event.undoCommand(args) })
    }

    private fun MessageReceivedEvent.listCommand(args: List<String>) {
        val list = Database.listKeywords()
        val keywords = list.joinToString(", !", prefix = "!")
        reply(if (list.isNotEmpty()) "📌 All ${list.size} keywords: $keywords" else "No keywords set.")
    }

    private fun MessageReceivedEvent.addCommand(args: List<String>) {
        if (args.size < 3) return
        val keyword = args[1]
        val response = args.drop(2).joinToString(" ")
        if (Database.listKeywords().contains(keyword.lowercase())) {
            reply("❌ Keyword already exists. Use `!tagedit` instead.")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            message.messageDeleteAndThen {
                val id = author.id
                reply("✅ Keyword '$keyword' added by <@$id>:")
                reply(response)
                logAction("added keyword '$keyword'")
                logAction("response: '$response'")
            }
        } else {
            reply("❌ Failed to add keyword.")
        }
    }

    private fun MessageReceivedEvent.editCommand(args: List<String>) {
        if (args.size < 3) return
        val keyword = args[1]
        val response = args.drop(2).joinToString(" ")
        val oldResponse = Database.getResponse(keyword)
        if (oldResponse == null) {
            reply("❌ Keyword doesn't exist! Use `!tagadd` instead.")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            message.messageDeleteAndThen {
                val id = author.id
                reply("✅ Keyword '$keyword' edited by <@$id>:")
                reply(response)
                logAction("edited keyword '$keyword'")
                logAction("old response: '$oldResponse'")
                logAction("new response: '$response'")
            }
        } else {
            reply("❌ Failed to edit keyword.")
        }
    }

    private fun MessageReceivedEvent.deleteCommand(args: List<String>) {
        if (args.size < 2) return
        val keyword = args[1]
        val oldResponse = Database.getResponse(keyword)
        if (Database.deleteKeyword(keyword)) {
            reply("🗑️ Keyword '$keyword' deleted!")
            logAction("deleted keyword '$keyword'")
            logAction("response was: '$oldResponse'")
        } else {
            reply("❌ Keyword '$keyword' not found.")
        }
    }

    private fun MessageReceivedEvent.undoCommand(args: List<String>) {
        val author = author.id
        val message = message
        if (undo(author)) {
            logAction("undid last send tag.")
            message.messageDelete()
        } else {
            addLastMessage(author, message)
            message.replyWithConsumer("No last tag to undo found!") { consumer ->
                addLastMessage(author, consumer.message)
            }
            Utils.runDelayed(2.seconds) {
                undo(author)
            }
        }
    }

    private fun undo(author: String): Boolean {
        return lastMessages[author]?.let {
            for (message in it) {
                message.messageDelete()
            }
            lastMessages.remove(author)
            true
        } ?: false
    }

    fun handleTag(event: MessageReceivedEvent): Boolean {
        val message = event.message
        var keyword = message.contentRaw.substring(1)
        var deleting = false
        if (keyword.endsWith(" -d")) {
            keyword = keyword.dropLast(3)
            deleting = true
        }
        val response = Database.getResponse(keyword) ?: run {
            event.reply("Unknown command \uD83E\uDD7A Type `!help` for help.")
            return false
        }

        val author = message.author.id
        val channelName = event.channel.name
        message.referencedMessage?.let {
            event.logAction("used reply keyword '$keyword' in channel '$channelName'")
            message.messageDelete()
            it.replyWithConsumer(response) { consumer ->
                addLastMessage(author, consumer.message)
            }
        } ?: run {
            if (deleting) {
                event.logAction("used keyword with delete '$keyword' in channel '$channelName'")
                message.messageDeleteAndThen {
                    event.channel.sendMessageWithConsumer(response) { consumer ->
                        addLastMessage(author, consumer.message)
                    }
                }
            } else {
                addLastMessage(author, message)
                message.replyWithConsumer(response) { consumer ->
                    addLastMessage(author, consumer.message)
                }
            }
        }
        return true
    }

    private fun addLastMessage(author: String, message: Message) {
        lastMessages.getOrPut(author) { mutableListOf() }.add(message)
    }
}