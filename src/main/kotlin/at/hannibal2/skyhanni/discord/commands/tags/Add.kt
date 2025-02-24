package at.hannibal2.skyhanni.discord.commands.tags

import at.hannibal2.skyhanni.discord.Command
import at.hannibal2.skyhanni.discord.CommandInfo
import at.hannibal2.skyhanni.discord.CommandOption
import at.hannibal2.skyhanni.discord.handlers.Database
import at.hannibal2.skyhanni.discord.util.EventUtil.logAction
import at.hannibal2.skyhanni.discord.util.EventUtil.missingArguments
import at.hannibal2.skyhanni.discord.util.EventUtil.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class Add : Command {
    override val category = "Tags"
    override val permissions = "Staff"
    override val info = CommandInfo(
        name = "add",
        description = "Add a tag to the database.",
        options = listOf(
            CommandOption(
                name = "keyword",
                description = "Keyword you want the tag to have.",
                required = true
            ),
            CommandOption(
                name = "response",
                description = "Response you want to add.",
                required = true,
            )
        )
    )

    override fun execute(event: MessageReceivedEvent, args: List<String>) {
        if (event.missingArguments(args, info.options)) return

        val keyword = args[0]
        val response = args[1]

        if (Database.listKeywords().contains(keyword.lowercase())) {
            event.reply("❌ Already exists use `!edit` instead.")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            event.reply("✅ Keyword '$keyword' added!")
            event.logAction("added keyword '$keyword'")
            event.logAction("response: '$response'")
        } else {
            event.reply("❌ Failed to add keyword.")
        }
    }
}