package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.*
import at.hannibal2.skyhanni.discord.Utils.asUserMention
import at.hannibal2.skyhanni.discord.Utils.getLinkName
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
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import kotlin.time.Duration.Companion.seconds

object TagCommands {
    internal val lastTouchedTag = mutableMapOf<String, String>()

    // user id -> list of messages
    internal val lastMessages = mutableMapOf<String, MutableList<Message>>()

    internal fun addLastMessage(author: String, message: Message) {
        lastMessages.getOrPut(author) { mutableListOf() }.add(message)
    }

    internal fun tagChoices(input: String): List<String> =
        Database.listTags()
            .filter { it.keyword.contains(input, ignoreCase = true) }
            .sortedByDescending { it.uses }
            .map { it.keyword }

    fun handleTag(event: MessageReceivedEvent): Boolean {
        val message = event.message
        var keyword = message.contentRaw.substring(1).trimStart()
        val deleting = keyword.endsWith(" -d")
        if (deleting) keyword = keyword.dropLast(3)
        val info = keyword.endsWith(" -i")
        if (info) keyword = keyword.dropLast(3)

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
        val remember: (Message) -> Unit = { addLastMessage(author, it) }
        val referenced = message.referencedMessage
        when {
            referenced != null -> {
                event.logAction("used tag '$keyword' (with reply)")
                message.messageDelete()
                referenced.replyWithConsumer(response, remember)
            }

            deleting -> {
                event.logAction("used tag '$keyword' (with delete)")
                message.messageDeleteAndThen {
                    event.channel.sendMessageWithConsumer(response, remember)
                }
            }

            else -> {
                event.logAction("used tag '$keyword'")
                addLastMessage(author, message)
                message.replyWithConsumer(response, remember)
            }
        }
        return true
    }
}

@Suppress("unused")
@SkyHanniSlashCommand(
    name = "tag",
    description = "Displays the response of a tag.",
    parameters = [
        SkyHanniSlashCommandParameter("tag", "The tag you want to display.", autoComplete = true),
        SkyHanniSlashCommandParameter("user", "The user to ping with the tag.", required = false, type = OptionType.USER),
    ],
)
class TagShow : BaseCommand() {
    override fun CommandEvent.execute(args: List<String>) {
        if (args.size !in 1..2) return wrongUsage("<tag> [user]")
        val keyword = args.first()
        val response = Database.getResponse(keyword, increment = true) ?: run {
            userError("Unknown tag `$keyword` $PLEADING_FACE")
            return
        }
        val text = args.getOrNull(1)?.let { "${it.asUserMention()} $response" } ?: response

        val authorId = author.id
        lastTouchedTag[authorId] = keyword
        logAction("used tag '$keyword'")
        message?.let { addLastMessage(authorId, it) }
        replyWithConsumer(text) { sentMessage ->
            addLastMessage(authorId, sentMessage)
        }
    }

    override fun autoCompleteChoices(member: Member, optionName: String, input: String): List<String> =
        TagCommands.tagChoices(input)
}

@Suppress("unused")
@SkyHanniSlashCommand(
    name = "taglist",
    description = "Lists all available tags.",
    aliases = ["tags"],
    parameters = [SkyHanniSlashCommandParameter("info", "Include and sort by uses (-i to use).", required = false)],
)
class TagList : BaseCommand() {
    override fun CommandEvent.execute(args: List<String>) {
        val list = Database.listTags()
        if (list.isEmpty()) {
            reply("No tags set.")
            return
        }

        val tags = if (args.firstOrNull() == "-i") {
            val sortedList = list.sortedWith(compareByDescending<Tag> { it.uses }.thenBy { it.keyword })
            sortedList.joinToString("\n") { "!${it.keyword} (${it.uses} uses)" }

        } else {
            list.joinToString(", !", prefix = "!") { it.keyword }
        }
        reply("📌 All ${list.size} tags:\n$tags")
    }
}

@Suppress("unused")
@SkyHanniSlashCommand(
    name = "tagedit",
    description = "Edits a tag in the database.",
    aliases = ["tagchange"],
    parameters = [
        SkyHanniSlashCommandParameter("tag", "The tag you want to edit.", autoComplete = true),
        SkyHanniSlashCommandParameter("response", "Response you want the tag to have."),
    ],
    allowedRoles = [PermissionRole.DISCORD_MODERATOR],
)
class TagEdit : BaseCommand() {
    override fun autoCompleteChoices(member: Member, optionName: String, input: String): List<String> =
        TagCommands.tagChoices(input)

    override fun CommandEvent.execute(args: List<String>) {
        if (args.size < 2) return wrongUsage("<tag> <response>")
        val keyword = args.first()
        val response = args.drop(1).joinToString(" ")
        val oldResponse = Database.getResponse(keyword)
        val count = Database.getTagCount(keyword) ?: 0
        if (oldResponse == null) {
            userError("❌ Tag doesn't exist! Use `!tagadd` instead.")
            return
        }
        if (Database.addTag(keyword, response, count)) {
            deleteInvocation {
                reply("✅ Tag '$keyword' edited by ${author.getLinkName()}:")
                reply(response)
                logAction("edited tags '$keyword'")
                logAction("old response: '$oldResponse'", raw = true)
                logAction("new response: '$response'", raw = true)
                lastTouchedTag[author.id] = keyword
            }
        } else {
            reply("❌ Failed to edit tags.")
        }
    }
}

@Suppress("unused")
@SkyHanniSlashCommand(
    name = "tageditlast",
    description = "Show info on how to edit the last tag used.",
    allowedRoles = [PermissionRole.DISCORD_MODERATOR],
)
class TagEditLast : BaseCommand() {
    override fun CommandEvent.execute(args: List<String>) {
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
@SkyHanniSlashCommand(
    name = "tagadd",
    description = "Adds a tag to the database.",
    aliases = ["tagcreate"],
    parameters = [
        SkyHanniSlashCommandParameter("keyword", "Keyword you want the tag to have."),
        SkyHanniSlashCommandParameter("response", "Response you want the tag to have."),
    ],
    allowedRoles = [PermissionRole.DISCORD_MODERATOR],
)
class TagAdd : BaseCommand() {
    override fun CommandEvent.execute(args: List<String>) {
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
            deleteInvocation {
                reply("✅ Tag '$keyword' added by ${author.getLinkName()}:")
                reply(response)
                logAction("added tag '$keyword'")
                logAction("response: '$response'", raw = true)
                lastTouchedTag[author.id] = keyword
            }
        } else {
            reply("❌ Failed to add keyword.")
        }
    }
}

@Suppress("unused")
@SkyHanniSlashCommand(
    name = "tagdelete",
    description = "Deletes a tag from the database.",
    aliases = ["tagremove"],
    parameters = [SkyHanniSlashCommandParameter("keyword", "Keyword of the tag you want to delete.", autoComplete = true)],
    allowedRoles = [PermissionRole.DISCORD_MODERATOR],
)
class TagDelete : BaseCommand() {
    override fun autoCompleteChoices(member: Member, optionName: String, input: String): List<String> =
        TagCommands.tagChoices(input)

    override fun CommandEvent.execute(args: List<String>) {
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
@SkyHanniSlashCommand(
    name = "tagundo",
    description = "Deletes the last sent tag from that user.",
    aliases = ["undo"],
)
object TagUndo : BaseCommand() {
    override fun CommandEvent.execute(args: List<String>) {
        val author = author.id
        if (undo(author)) {
            logAction("undid last send tag.")
            lastTouchedTag.remove(author)
            val invocation = message
            if (invocation != null) invocation.messageDelete() else reply("Undid the last tag $CHECK_MARK")
        } else {
            message?.let { addLastMessage(author, it) }
            replyWithConsumer("No last tag to undo found!") { sentMessage ->
                addLastMessage(author, sentMessage)
            }
            Utils.runDelayed("undo last tag", 2.seconds) {
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