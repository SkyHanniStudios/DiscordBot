package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.CommandListener
import at.hannibal2.skyhanni.discord.Database
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.doWhen
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.messageDeleteAndThen
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.runDelayed
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
        val response = Database.getResponse(keyword) ?: run {
            reply("Unknown command $PLEADING_FACE Type `!help` for help.", event)
            return false
        }

        val author = message.author.id
        lastTouchedTag[author] = keyword
        message.referencedMessage?.let {
            logAction("used keyword '$keyword' (with reply)", event)
            message.messageDelete()
            it.replyWithConsumer(response) { consumer ->
                addLastMessage(author, consumer.message)
            }
        } ?: run {
            if (deleting) {
                logAction("used keyword '$keyword' (with delete)", event)
                message.messageDeleteAndThen {
                    event.channel.sendMessageWithConsumer(response) { consumer ->
                        addLastMessage(author, consumer.message)
                    }
                }
            } else {
                logAction("used keyword '$keyword'", event)
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

    override fun execute(args: List<String>, event: Any) {
        val list = Database.listKeywords()
        val keywords = list.joinToString(", !", prefix = "!")
        reply(if (list.isNotEmpty()) "📌 All ${list.size} keywords: $keywords" else "No keywords set.", event)
    }
}

@Suppress("unused")
class TagEdit : BaseCommand() {
    override val name = "tagedit"
    override val description = "Edits a tag in the database."
    override val options: List<Option> = listOf(
        Option("keyword", "The tag you want to edit.", autoComplete = true),
        Option("response", "Response you want the tag to have.")
    )
    override val aliases = listOf("tagchange")

    override fun execute(args: List<String>, event: Any) {
        val keyword = doWhen(event,
            {
                if (args.size < 2) {
                    wrongUsage("<keyword> <response>", event)
                    return@doWhen null
                }

                args.first()
            },
            { it.getOption("keyword")?.asString }
        ) ?: return

        val response =
            doWhen(event, { args.drop(1).joinToString(" ") }, { it.getOption("response")?.asString }) ?: return

        val oldResponse = Database.getResponse(keyword)
        if (oldResponse == null) {
            userError("Keyword doesn't exist! Use `!tagadd` instead.", event, ephemeral = true)
            return
        }
        if (Database.addKeyword(keyword, response)) {
            doWhen(event, { it.message.messageDelete() }, {})

            val id = doWhen(event, { it.author.id }, { it.user.id }).toString()

            reply("✅ Keyword '$keyword' edited by <@$id>:", event)
            doWhen(event, { reply(response, it) }, { it.channel.sendMessage(response).queue() })
            logAction("edited keyword '$keyword'", event)
            logAction("old response: '$oldResponse'", event, raw = true)
            logAction("new response: '$response'", event, raw = true)
            lastTouchedTag[id] = keyword
        } else {
            sendError("❌ Failed to edit keyword.", event, ephemeral = true)
        }
    }
}

@Suppress("unused")
class TagEditLast : BaseCommand() {
    override val name = "tageditlast"
    override val description = "Show info on how to edit the last tag used."

    override fun execute(args: List<String>, event: Any) {
        val id = doWhen(event, { it.author.id }, { it.user.id }).toString()

        val lastTag = lastTouchedTag[id] ?: run {
            return userError("No last tag found $PLEADING_FACE", event, ephemeral = true)
        }
        val response = Database.getResponse(lastTag) ?: run {
            return sendError(
                "Last tag `$lastTag` got deleted, this should not happen, therefore we ping <@239858538959077376>",
                event,
                ephemeral = true
            )
        }
        reply("```!tagedit $lastTag $response```", event, ephemeral = true)
    }
}

@Suppress("unused")
class TagAdd : BaseCommand() {
    override val name = "tagadd"
    override val description = "Adds a tag to the database."
    override val options: List<Option> = listOf(
        Option("keyword", "Keyword you want the tag to have."),
        Option("response", "Response you want the tag to have.")
    )
    override val aliases = listOf("tagcreate")

    override fun execute(args: List<String>, event: Any) {
        val keyword = doWhen(event, {
            if (args.size < 2) {
                wrongUsage("<keyword> <response>", event)
                return@doWhen null
            }
            args.first()
        }, {
            it.getOption("keyword")?.asString
        }) ?: return


        if (CommandListener.existsCommand(keyword)) {
            return userError(
                "Can not create keyword `!$keyword`. There is already a command with that name",
                event,
                ephemeral = true
            )
        }
        val response =
            doWhen(event, { args.drop(1).joinToString(" ") }, { it.getOption("response")?.asString }) ?: return

        if (Database.containsKeyword(keyword)) {
            return userError("Keyword already exists. Use `!tagedit` instead.", event, ephemeral = true)
        }
        if (Database.addKeyword(keyword, response)) {
            doWhen(event, { it.message.messageDelete() }, {})
            val author = doWhen(event, { it.author }, { it.user }) ?: return

            reply("✅ Keyword '$keyword' added by ${author.asMention}:", event)
            doWhen(event, { reply(response, event) }, { it.channel.sendMessage(response).queue() })
            logAction("added keyword '$keyword'", event)
            logAction("response: '$response'", event, raw = true)
            lastTouchedTag[author.id] = keyword
        } else {
            sendError("❌ Failed to add keyword.", event, ephemeral = true)
        }
    }
}

@Suppress("unused")
class TagDelete : BaseCommand() {
    override val name: String = "tagdelete"
    override val description: String = "Deletes a tag from the database."
    override val options: List<Option> = listOf(
        Option("keyword", "Keyword of the tag you want to delete.", autoComplete = true)
    )
    override val aliases: List<String> = listOf("tagremove")

    override fun execute(args: List<String>, event: Any) {
        val keyword = doWhen(event, {
            if (args.isEmpty()) {
                wrongUsage("<keyword>", event)
                return@doWhen null
            }

            args.first()
        }, { it.getOption("keyword")?.asString }) ?: return

        val oldResponse = Database.getResponse(keyword)

        if (Database.deleteKeyword(keyword)) {
            reply("🗑️ Keyword '$keyword' deleted!", event)
            logAction("deleted keyword '$keyword'", event)
            logAction("response was: '$oldResponse'", event, raw = true)
            val id = doWhen(event, { it.author.id }, { it.user.id })
            lastTouchedTag.remove(id)
        } else {
            userError("Keyword '$keyword' not found.", event, ephemeral = true)
        }
    }
}

@Suppress("unused")
object TagUndo : BaseCommand() {
    override val name: String = "tagundo"
    override val description: String = "Undoes something not quite sure."
    override val userCommand: Boolean = true
    override val aliases: List<String> = listOf("undo")

    override fun execute(args: List<String>, event: Any) {
        val author = doWhen(event, { it.author.id }, { it.user.id }) ?: return
        if (undo(author)) {
            logAction("undid last send tag.", event)
            lastTouchedTag.remove(author)
            doWhen(event, { it.message.messageDelete() }, { reply("Undid last sent tag.", event, ephemeral = true) })
        } else {
            doWhen(
                event, {
                    addLastMessage(author, it.message)
                    it.message.replyWithConsumer("No last tag to undo found!") { consumer ->
                        addLastMessage(author, consumer.message)
                    }
                    runDelayed(2.seconds) {
                        undo(author)
                    }
                }, {
                    sendError("No last tag to undo found!", event)
                }
            )
        }
    }
}

fun undo(author: String): Boolean {
    return lastMessages[author]?.let {
        for (message in it) {
            message.messageDelete()
        }
        lastMessages.remove(author)
        true
    } ?: false
}