package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.CommandListener
import at.hannibal2.skyhanni.discord.Database
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.messageDeleteAndThen
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.sendError
import at.hannibal2.skyhanni.discord.Utils.sendMessageWithConsumer
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.command.TagCommands.addLastMessage
import at.hannibal2.skyhanni.discord.command.TagCommands.lastMessages
import at.hannibal2.skyhanni.discord.command.TagCommands.lastTouchedTag
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import kotlin.time.Duration.Companion.seconds

object TagCommands {
    internal val lastTouchedTag = mutableMapOf<String, String>()

    // user id -> list of messages
    internal val lastMessages = mutableMapOf<String, MutableList<Message>>()

    internal fun addLastMessage(author: String, message: Message) {
        lastMessages.getOrPut(author) { mutableListOf() }.add(message)
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
            val count = Database.getTagCount(keyword)
            event.reply("Tag `!$keyword` got used $count times in total.")
            return true
        }

        val author = message.author.id
        lastTouchedTag[author] = keyword
        message.referencedMessage?.let {
            event.logAction("used tag '$keyword' (with reply)")
            message.messageDelete()
            it.replyWithConsumer(response) { consumer ->
                addLastMessage(author, consumer.message)
            }
        } ?: run {
            if (deleting) {
                event.logAction("used tag '$keyword' (with delete)")
                message.messageDeleteAndThen {
                    event.channel.sendMessageWithConsumer(response) { consumer ->
                        addLastMessage(author, consumer.message)
                    }
                }
            } else {
                event.logAction("used tag '$keyword'")
                addLastMessage(author, message)
                message.replyWithConsumer(response) { consumer ->
                    addLastMessage(author, consumer.message)
                }
            }
        }
        return true
    }
}

@Suppress("unused")
class TagList : BaseCommand() {
    override val name = "taglist"
    override val description = "Lists all available tags."
    override val aliases = listOf("tags")
    override val userCommand: Boolean = true

    override fun MessageReceivedEvent.execute(args: List<String>) {
        val list = Database.listTags()
        if (list.isEmpty()) {
            reply("No tags set.")
            return
        }

        val tags = if (args.firstOrNull() == "-i") {
            list.sortedByDescending { it.uses }.joinToString("\n") { "!${it.keyword} (${it.uses} uses)" }
        } else {
            list.joinToString(", !", prefix = "!") { it.keyword }
        }
        reply("📌 All ${list.size} tags:\n$tags")
    }
}

@Suppress("unused")
class TagEdit : BaseCommand() {
    override val name = "tagedit"
    override val description = "Edits a tag in the database."
    override val options: List<Option> = listOf(
        Option("tag", "The tag you want to edit."), Option("response", "Response you want the tag to have.")
    )
    override val aliases = listOf("tagchange")

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (args.size < 2) return wrongUsage("<tag> <response>")
        val keyword = args.first()
        val response = args.drop(1).joinToString(" ")
        val oldResponse = Database.getResponse(keyword)
        if (oldResponse == null) {
            userError("❌ Tag doesn't exist! Use `!tagadd` instead.")
            return
        }
        if (Database.addTag(keyword, response)) {
            message.messageDeleteAndThen {
                val id = author.id
                reply("✅ Tag '$keyword' edited by <@$id>:")
                reply(response)
                logAction("edited tags '$keyword'")
                logAction("old response: '$oldResponse'", raw = true)
                logAction("new response: '$response'", raw = true)
                lastTouchedTag[id] = keyword
            }
        } else {
            reply("❌ Failed to edit tags.")
        }
    }
}

@Suppress("unused")
class TagEditLast : BaseCommand() {
    override val name = "tageditlast"
    override val description = "Show info on how to edit the last tag used."

    override fun MessageReceivedEvent.execute(args: List<String>) {
        val id = author.id
        val lastTag = lastTouchedTag[id] ?: run {
            return userError("No last tag found $PLEADING_FACE")
        }
        val response = Database.getResponse(lastTag) ?: run {
            return sendError("Last tag `$lastTag` got deleted, this should not happen, therefore we ping $PING_HANNIBAL")
        }
        reply("```!tagedit $lastTag $response```")
    }
}

@Suppress("unused")
class TagAdd : BaseCommand() {
    override val name = "tagadd"
    override val description = "Adds a tag to the database."
    override val options: List<Option> = listOf(
        Option("keyword", "Keyword you want the tag to have."), Option("response", "Response you want the tag to have.")
    )
    override val aliases = listOf("tagcreate")

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (args.size < 2) return wrongUsage("<keyword> <response>")

        val keyword = args.first()
        if (CommandListener.existsCommand(keyword)) {
            reply("❌ Can not create tag `!$keyword`. There is already a command with that name")
            return
        }
        val response = args.drop(1).joinToString(" ")

        if (Database.containsKeyword(keyword)) {
            reply("❌ Tag already exists. Use `!tagedit` instead.")
            return
        }
        if (Database.addTag(keyword, response)) {
            message.messageDeleteAndThen {
                val id = author.id
                reply("✅ Tag '$keyword' added by <@$id>:")
                reply(response)
                logAction("added tag '$keyword'")
                logAction("response: '$response'", raw = true)
                lastTouchedTag[id] = keyword
            }
        } else {
            reply("❌ Failed to add keyword.")
        }
    }
}

@Suppress("unused")
class TagDelete : BaseCommand() {
    override val name: String = "tagdelete"
    override val description: String = "Deletes a tag from the database."
    override val options: List<Option> = listOf(
        Option("keyword", "Keyword of the tag you want to delete.")
    )
    override val aliases: List<String> = listOf("tagremove")

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (args.size != 1) return wrongUsage("<keyword>")

        val keyword = args.first()
        val oldResponse = Database.getResponse(keyword)
        if (Database.deleteTag(keyword)) {
            reply("🗑️ Tag '$keyword' deleted!")
            logAction("deleted tag '$keyword'")
            logAction("response was: '$oldResponse'", raw = true)
            val id = author.id
            lastTouchedTag.remove(id)
        } else {
            reply("❌ Tag '$keyword' not found.")
        }
    }
}

@Suppress("unused")
object TagUndo : BaseCommand() {
    override val name: String = "tagundo"
    override val description: String = "Undoes something not quite sure."
    override val userCommand: Boolean = true
    override val aliases: List<String> = listOf("undo")

    override fun MessageReceivedEvent.execute(args: List<String>) {
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

}