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
    private val lastMessages = mutableMapOf<String, MutableList<Message>>()

    init {
        commands.add(Command("list") { event, args -> event.listCommand(args) })
        commands.add(Command("taglist") { event, args -> event.listCommand(args) })

        commands.add(Command("edit") { event, args -> event.editCommand(args) })
        commands.add(Command("change") { event, args -> event.editCommand(args) })

        commands.add(Command("add") { event, args -> event.addCommand(args) })
        commands.add(Command("delete") { event, args -> event.deleteCommand(args) })
        commands.add(Command("remove") { event, args -> event.deleteCommand(args) })

        // removes the last !tag action
        commands.add(Command("undo") { event, args -> event.undoCommand(args) })
    }

    private fun MessageReceivedEvent.listCommand(args: List<String>) {
        val keywords = Database.listKeywords().joinToString(", ")
        val replyMsg = if (keywords.isNotEmpty()) "📌 Keywords: $keywords" else "No keywords set."
        reply(replyMsg)
    }

    private fun MessageReceivedEvent.addCommand(args: List<String>) {
        if (!hasEditPermission(this)) return

        if (args.size < 3) return
        val keyword = args[1]
        val response = args[2]
        if (Database.listKeywords().contains(keyword.lowercase())) {
            reply("❌ Already exists. Use `!edit` instead.")
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
        if (channel.id != config.botCommandChannelId) return
        if (!hasEditPermission(this)) return

        if (args.size < 3) return
        val keyword = args[1]
        val response = args[2]
        val oldResponse = Database.getResponse(keyword)
        if (oldResponse == null) {
            reply("❌ Keyword doesn't exist! Use `!add` instead.")
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
        if (channel.id != config.botCommandChannelId) return
        if (!hasEditPermission(this)) return

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
            if (hasEditPermission(event)) {
                val s = "Unknown command \uD83E\uDD7A Type `!help` for help."
                event.reply(s)
            }
            return false
        }

        val author = message.author.id
        val channelName = event.channel.name
        lastMessages.remove(author)
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

    private fun hasEditPermission(event: MessageReceivedEvent): Boolean {
        if (event.channel.id != config.botCommandChannelId) return false
        val member = event.member ?: return false
        val allowedRoleIds = config.editPermissionRoleIds.values
        if (member.roles.none { it.id in allowedRoleIds }) {
            event.reply("No perms \uD83E\uDD7A")
            return false
        }
        return true
    }

}