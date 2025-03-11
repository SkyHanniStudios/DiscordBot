package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.Option

abstract class BaseCommand {

    abstract val name: String

    abstract val description: String

    open val options: List<Option> = emptyList()

    open val userCommand: Boolean = false

    protected open val aliases: List<String> = emptyList()

    abstract fun CommandEvent.execute(args: List<String>)

    protected fun CommandEvent.wrongUsage(args: String) {
        userError("Usage: `!$name $args`")
    }

    fun getAllNames(): List<String> = aliases + name
}