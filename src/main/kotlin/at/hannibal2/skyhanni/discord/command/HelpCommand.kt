package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.CommandEvent
import at.hannibal2.skyhanni.discord.CommandListener
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.hasPermissionFor
import at.hannibal2.skyhanni.discord.Utils.hasRoleOrAbove
import at.hannibal2.skyhanni.discord.Utils.reply
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import java.awt.Color

@SkyHanniSlashCommand(
    name = "help",
    description = "Get help for all OR one specific command.",
    parameters = [
        SkyHanniSlashCommandParameter(
            "command",
            "Command you want to get help for.",
            required = false,
            autoComplete = true
        )
    ],
)
object HelpCommand : BaseCommand() {
    override fun autoCompleteChoices(member: Member, optionName: String, input: String): List<String> =
        CommandListener.commands
            .filter { cmd -> cmd.allowedRoles.isEmpty() || cmd.allowedRoles.any { member.hasRoleOrAbove(it) } }
            .map { it.name }
            .filter { it.startsWith(input, ignoreCase = true) }

    override fun CommandEvent.execute(args: List<String>) {
        if (args.size > 1) return reply("Usage: ${prefix}help <command>")

        if (args.size == 1) {
            sendUsageReply(args.first().lowercase())
        } else {
            val commands = CommandListener.commands.filter { hasPermissionFor(it) }
            val list = commands.joinToString(", $prefix") { it.name }
            reply("Supported commands: $prefix$list")
        }
    }

    fun CommandEvent.sendUsageReply(commandName: String) {
        val command = CommandListener.getCommand(commandName) ?: run {
            reply("Unknown command `$prefix$commandName` $PLEADING_FACE")
            return
        }

        if (!hasPermissionFor(command)) {
            reply("No permissions for command `$prefix$commandName` $PLEADING_FACE")
            return
        }

        this.reply(command.createHelpEmbed(commandName, prefix))
    }

    private fun BaseCommand.createHelpEmbed(commandName: String, prefix: String): MessageEmbed {
        val em = EmbedBuilder()

        val opts = if (this.options.isNotEmpty()) {
            val mapped = this.options.joinToString(" ") { if (it.required) "<${it.name}>" else "[${it.name}]" }
            " $mapped"
        } else ""
        em.setTitle("Usage: $prefix$commandName$opts")
        em.setDescription("📋 **${this.description}**")
        em.setColor(Color.GREEN)

        if (allowedRoles.isNotEmpty()) {
            val rolesStr = allowedRoles.sortedByDescending { it.ordinal }.joinToString(", ") { it.name }
            em.addField("Required Roles", rolesStr, false)
        }

        for (option in this.options) {
            em.addField(option.name, option.description, true)
            em.addField("Required", if (option.required) "✅" else "❌", true)
            em.addBlankField(true)
        }

        return em.build()
    }
}