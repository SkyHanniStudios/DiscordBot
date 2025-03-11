package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.Utils.userError

abstract class BaseCommand {

    abstract val name: String

    abstract val description: String

    open val options: List<Option> = emptyList()

    open val userCommand: Boolean = false

    protected open val aliases: List<String> = emptyList()

    abstract fun execute(args: List<String>, event: Any)

    protected fun wrongUsage(args: String, event: Any) {
        userError("Usage: `!$name $args`", event)
    }

    fun getAllNames(): List<String> = aliases + name
}