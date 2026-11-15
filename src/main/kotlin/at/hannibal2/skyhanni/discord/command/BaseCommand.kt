package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.CommandEvent
import at.hannibal2.skyhanni.discord.PermissionRole
import at.hannibal2.skyhanni.discord.Utils.userError
import net.dv8tion.jda.api.entities.Member

abstract class BaseCommand {

    private val definition: SkyHanniSlashCommand = javaClass.getAnnotation(SkyHanniSlashCommand::class.java)
        ?: error("${javaClass.name} is missing @SkyHanniSlashCommand")

    val name: String get() = definition.name

    val description: String get() = definition.description

    val options: List<SkyHanniSlashCommandParameter> = definition.parameters.toList()

    val allowedRoles: Set<PermissionRole> = definition.allowedRoles.toSet()

    val supportsSlash: Boolean get() = definition.supportsSlash

    abstract fun CommandEvent.execute(args: List<String>)

    /**
     * Choices offered while the user types into an option that has [SkyHanniSlashCommandParameter.autoComplete] set.
     * Discord displays at most 25 of them.
     *
     * Commands with more than one autocompleting option have to branch on [optionName], otherwise
     * all of them receive the same choices.
     */
    open fun autoCompleteChoices(member: Member, optionName: String, input: String): List<String> = emptyList()

    protected fun CommandEvent.wrongUsage(args: String) {
        userError("Usage: `$prefix$name $args`")
    }

    fun getAllNames(): List<String> = definition.aliases.toList() + name
}
