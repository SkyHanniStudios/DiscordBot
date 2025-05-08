package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.Utils.userError
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

abstract class BaseCommand {

    abstract val name: String

    abstract val description: String

    open val options: List<Option> = emptyList()

    open val userCommand: Boolean = false

    open val async: Boolean = false

    protected open val aliases: List<String> = emptyList()

    abstract fun MessageReceivedEvent.execute(args: List<String>)

    protected fun MessageReceivedEvent.wrongUsage(args: String) {
        userError("Usage: `!$name $args`")
    }

    fun getAllNames(): List<String> = aliases + name
}