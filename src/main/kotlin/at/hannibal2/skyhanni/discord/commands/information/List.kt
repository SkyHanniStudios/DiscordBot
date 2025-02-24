package at.hannibal2.skyhanni.discord.commands.information

import at.hannibal2.skyhanni.discord.Command
import at.hannibal2.skyhanni.discord.CommandInfo
import at.hannibal2.skyhanni.discord.handlers.Database
import at.hannibal2.skyhanni.discord.util.EventUtil.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import kotlin.collections.List

class List : Command {
    override val category = "Information"
    override val permissions = "User"
    override val info = CommandInfo(
        name = "list",
        description = "List all available tags.",
    )

    override fun execute(event: MessageReceivedEvent, args: List<String>) {
        val keywords = Database.listKeywords().joinToString(", ")
        event.reply(if (keywords.isNotEmpty()) "📌 Keywords: $keywords" else "No keywords set.")
    }
}