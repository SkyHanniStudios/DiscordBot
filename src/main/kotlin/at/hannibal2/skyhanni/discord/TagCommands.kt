package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.sendMessageWithConsumer
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

@Suppress("UNUSED_PARAMETER")
class TagCommands(private val config: BotConfig, commands: Commands) {
    private val lastMessages = mutableMapOf<String, MutableList<Message>>()

    init {
        commands.add(Command("list", ::listCommand))
        commands.add(Command("taglist", ::listCommand))

        commands.add(Command("edit", ::editCommand))
        commands.add(Command("change", ::editCommand))

        commands.add(Command("add", ::addCommand))
        commands.add(Command("delete", ::deleteCommand))
        commands.add(Command("remove", ::deleteCommand))

        // removes the last !tag action
        commands.add(Command("undo", ::undoCommand))
    }

    private fun listCommand(event: MessageReceivedEvent, args: List<String>) {
        val keywords = Database.listKeywords().joinToString(", ")
        val replyMsg = if (keywords.isNotEmpty()) "📌 Keywords: $keywords" else "No keywords set."
        event.reply(replyMsg)
    }

    private fun addCommand(event: MessageReceivedEvent, args: List<String>) {
        if (!hasEditPermission(event)) return

        if (args.size < 3) return
        val keyword = args[1]
        val response = args[2]
        if (Database.listKeywords().contains(keyword.lowercase())) {
            event.reply("❌ Already exists. Use `!edit` instead.")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            event.message.delete().queue {
                val id = event.author.id
                event.reply("✅ Keyword '$keyword' added by <@$id>:")
                event.reply(response)
                event.logAction("added keyword '$keyword'")
                event.logAction("response: '$response'")
            }
        } else {
            event.reply("❌ Failed to add keyword.")
        }
    }

    private fun editCommand(event: MessageReceivedEvent, args: List<String>) {
        if (event.channel.id != config.botCommandChannelId) return
        if (!hasEditPermission(event)) return

        if (args.size < 3) return
        val keyword = args[1]
        val response = args[2]
        val oldResponse = Database.getResponse(keyword)
        if (oldResponse == null) {
            event.reply("❌ Keyword doesn't exist! Use `!add` instead.")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            event.message.delete().queue {
                val id = event.author.id
                event.reply("✅ Keyword '$keyword' edited by <@$id>:")
                event.reply(response)
                event.logAction("edited keyword '$keyword'")
                event.logAction("old response: '$oldResponse'")
                event.logAction("new response: '$response'")
            }
        } else {
            event.reply("❌ Failed to edit keyword.")
        }
    }

    private fun deleteCommand(event: MessageReceivedEvent, args: List<String>) {
        if (event.channel.id != config.botCommandChannelId) return
        if (!hasEditPermission(event)) return

        if (args.size < 2) return
        val keyword = args[1]
        val oldResponse = Database.getResponse(keyword)
        if (Database.deleteKeyword(keyword)) {
            event.reply("🗑️ Keyword '$keyword' deleted!")
            event.logAction("deleted keyword '$keyword'")
            event.logAction("response was: '$oldResponse'")
        } else {
            event.reply("❌ Keyword '$keyword' not found.")
        }
    }

    private fun undoCommand(event: MessageReceivedEvent, args: List<String>) {
        val author = event.author.id
        val message = event.message
        if (undo(author)) {
            message.delete().queue()
        } else {
            addLastMessage(author, message)
            message.replyWithConsumer("No last tag to undo found!") { consumer ->
                addLastMessage(author, consumer.message)
            }
            Thread {
                Thread.sleep(2_000)
                undo(author)
            }.start()
        }
    }

    private fun undo(author: String): Boolean {
        return lastMessages[author]?.let {
            for (message in it) {
                message.delete().queue()
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
            message.delete().queue()
            it.replyWithConsumer(response) { consumer ->
                addLastMessage(author, consumer.message)
            }
        } ?: run {
            if (deleting) {
                event.logAction("used keyword with delete '$keyword' in channel '$channelName'")
                message.delete().queue {
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