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
class TagCommands(private val config: BotConfig, private val commands: CommandListener) {
    val lastMessages = mutableMapOf<String, MutableList<Message>>()

    // user id -> tag keyword
    private val lastTouchedTag = mutableMapOf<String, String>()

    init {
        commands.add(Command("taglist", userCommand = true) { event, args -> event.listCommand(args) })
        commands.add(Command("tags", userCommand = true) { event, args -> event.listCommand(args) })

        commands.add(Command("tagedit") { event, args -> event.editCommand(args) })
        commands.add(Command("tagchange") { event, args -> event.editCommand(args) })
        commands.add(Command("tageditlast") { event, args -> event.editLastCommand(args) })

        commands.add(Command("tagadd") { event, args -> event.addCommand(args) })
        commands.add(Command("tagcreate") { event, args -> event.addCommand(args) })

        commands.add(Command("tagdelete") { event, args -> event.deleteCommand(args) })
        commands.add(Command("tagremove") { event, args -> event.deleteCommand(args) })

        commands.add(Command("undo", userCommand = true) { event, args -> event.undoCommand(args) })
    }

    private fun MessageReceivedEvent.listCommand(args: List<String>) {
        val list = Database.listKeywords()
        if (list.isEmpty()) {
            reply("No keywords set.")
            return
        }

        val keywords = if (args.size == 2 && args[1] == "-i") {
            list.sortedByDescending { it.uses }.joinToString("\n") { "!${it.keyword} (${it.uses} uses)" }
        } else {
            list.joinToString(", !", prefix = "!") { it.keyword }
        }
        reply("📌 All ${list.size} keywords:\n$keywords")
    }

    private fun MessageReceivedEvent.addCommand(args: List<String>) {
        if (args.size < 3) return
        val keyword = args[1]
        if (commands.existCommand(keyword)) {
            reply("❌ Can not create keyword `!$keyword`. There is already a command with that name")
            return
        }
        val response = args.drop(2).joinToString(" ")
        if (Database.listKeywords().map { it.keyword }.contains(keyword.lowercase())) {
            reply("❌ Keyword already exists. Use `!tagedit` instead.")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            message.messageDeleteAndThen {
                val id = author.id
                reply("✅ Keyword '$keyword' added by <@$id>:")
                reply(response)
                logAction("added keyword '$keyword'")
                logAction("response: '$response'", raw = true)
                lastTouchedTag[id] = keyword
            }
        } else {
            reply("❌ Failed to add keyword.")
        }
    }

    private fun MessageReceivedEvent.editCommand(args: List<String>) {
        if (args.size < 2) {
            reply("Usage: `!tagedit <tag> <response>`")
            return
        }
        val keyword = args[1]
        val response = args.drop(2).joinToString(" ")
        val oldResponse = Database.getResponse(keyword)
        if (oldResponse == null) {
            reply("❌ Keyword doesn't exist! Use `!tagadd` instead.")
            return
        }

        if (args.size < 3) {
            reply("Usage: `!tagedit <tag> <response>`\n```!tagedit $keyword $oldResponse```")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            message.messageDeleteAndThen {
                val id = author.id
                reply("✅ Keyword '$keyword' edited by <@$id>:")
                reply(response)
                logAction("edited keyword '$keyword'")
                logAction("old response: '$oldResponse'", raw = true)
                logAction("new response: '$response'", raw = true)
                lastTouchedTag[id] = keyword
            }
        } else {
            reply("❌ Failed to edit keyword.")
        }
    }

    private fun MessageReceivedEvent.editLastCommand(args: List<String>) {
        val id = author.id
        val lastTag = lastTouchedTag[id] ?: run {
            reply("No last tag found $PLEADING_FACE")
            return
        }
        val response = Database.getResponse(lastTag) ?: run {
            reply("Last tag `$lastTag` got deleted, this should not happen, therefore we ping <@239858538959077376>")
            return
        }
        reply("```!tagedit $lastTag $response```")
    }

    private fun MessageReceivedEvent.deleteCommand(args: List<String>) {
        if (args.size < 2) return
        val keyword = args[1]
        val oldResponse = Database.getResponse(keyword)
        if (Database.deleteKeyword(keyword)) {
            reply("🗑️ Keyword '$keyword' deleted!")
            logAction("deleted keyword '$keyword'")
            logAction("response was: '$oldResponse'", raw = true)
            val id = author.id
            lastTouchedTag.remove(id)
        } else {
            reply("❌ Keyword '$keyword' not found.")
        }
    }

    private fun MessageReceivedEvent.undoCommand(args: List<String>) {
        val author = author.id
        val message = message
        if (undo(author)) {
            logAction("undid last send tag.")
            lastTouchedTag.remove(author)
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
        var info = false
        if (keyword.endsWith(" -i")) {
            keyword = keyword.dropLast(3)
            info = true
        }
        val response = Database.getResponse(keyword, increment = !info) ?: run {
            event.reply("Unknown command $PLEADING_FACE Type `!help` for help.")
            return false
        }

        if (info) {
            val count = Database.getKeywordCount(keyword)
            event.reply("Tag `$keyword' got used $count times in total.")
            return true
        }

        val author = message.author.id
        lastTouchedTag[author] = keyword
        message.referencedMessage?.let {
            event.logAction("used keyword '$keyword' (with reply)")
            message.messageDelete()
            it.replyWithConsumer(response) { consumer ->
                addLastMessage(author, consumer.message)
            }
        } ?: run {
            if (deleting) {
                event.logAction("used keyword '$keyword' (with delete)")
                message.messageDeleteAndThen {
                    event.channel.sendMessageWithConsumer(response) { consumer ->
                        addLastMessage(author, consumer.message)
                    }
                }
            } else {
                event.logAction("used keyword '$keyword'")
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