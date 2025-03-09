package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.Option
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

abstract class BaseCommand {

    abstract val name: String

    abstract val description: String

    abstract val options: List<Option>

    open val userCommand: Boolean = false

    open val aliases: List<String> = emptyList()

    abstract fun MessageReceivedEvent.execute(args: List<String>)
}