package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.CommandEvent
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.Utils.userError

abstract class BaseCommand {

    abstract val name: String

    abstract val description: String

    open val options: List<Option> = emptyList()

    open val userCommand: Boolean = false

    /** False for commands that can not work without a message context, e.g. because they need a reply. */
    open val supportsSlash: Boolean = true

    protected open val aliases: List<String> = emptyList()

    abstract fun CommandEvent.execute(args: List<String>)

    protected fun CommandEvent.wrongUsage(args: String) {
        userError("Usage: `$prefix$name $args`")
    }

    fun getAllNames(): List<String> = aliases + name
}