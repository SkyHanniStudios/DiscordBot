package at.hannibal2.skyhanni.discord.commands.tags

import at.hannibal2.skyhanni.discord.Command
import at.hannibal2.skyhanni.discord.CommandInfo
import at.hannibal2.skyhanni.discord.CommandOption
import at.hannibal2.skyhanni.discord.handlers.Database
import at.hannibal2.skyhanni.discord.util.EventUtil.logAction
import at.hannibal2.skyhanni.discord.util.EventUtil.missingArguments
import at.hannibal2.skyhanni.discord.util.EventUtil.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class Edit : Command {
    override val category = "Tags"
    override val permissions = "Staff"
    override val info = CommandInfo(
        name = "edit",
        description = "Edit a tag from the database.",
        options = listOf(
            CommandOption(
                name = "keyword",
                description = "Keyword of the tag you want to edit.",
                required = true
            ),
            CommandOption(
                name = "response",
                description = "The new response you want the tag to have.",
                required= true
            )
        )
    )

    override fun execute(event: MessageReceivedEvent, args: List<String>) {
        if (event.missingArguments(args, info.options)) return

        val keyword = args[0]
        val response = args[1]

        val oldResponse = Database.getResponse(keyword)
        if (oldResponse == null) {
            event.reply("❌ Keyword does not exist! Use `!add` instead.")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            event.reply("✅ Keyword '$keyword' edited!")
            event.logAction("edited keyword '$keyword'")
            event.logAction("old response: '$oldResponse'")
            event.logAction("new response: '$response'")
        } else {
            event.reply("❌ Failed to edit keyword.")
        }
        return
    }
}