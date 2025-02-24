package at.hannibal2.skyhanni.discord.commands.tags

import at.hannibal2.skyhanni.discord.Command
import at.hannibal2.skyhanni.discord.CommandInfo
import at.hannibal2.skyhanni.discord.CommandOption
import at.hannibal2.skyhanni.discord.handlers.Database
import at.hannibal2.skyhanni.discord.util.EventUtil.logAction
import at.hannibal2.skyhanni.discord.util.EventUtil.missingArguments
import at.hannibal2.skyhanni.discord.util.EventUtil.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class Delete : Command {
    override val category = "Tags"
    override val permissions = "Staff"
    override val info = CommandInfo(
        name = "delete",
        description = "Delete a tag from the database.",
        options = listOf(
            CommandOption(
                name = "keyword",
                description = "Keyword of the tag you want to delete.",
                required = true
            ),
        )
    )

    override fun execute(event: MessageReceivedEvent, args: List<String>) {
        if (event.missingArguments(args, info.options)) return

        val keyword = args[0]

        val oldResponse = Database.getResponse(keyword)
        if (Database.deleteKeyword(keyword)) {
            event.reply("🗑️ Keyword '$keyword' deleted!")
            event.logAction("deleted keyword '$keyword'")
            event.logAction("response was: '$oldResponse'")
        } else {
            event.reply("❌ Keyword '$keyword' not found.")
        }
        return
    }
}