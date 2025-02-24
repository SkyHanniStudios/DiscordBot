package at.hannibal2.skyhanni.discord.commands.information

import at.hannibal2.skyhanni.discord.Command
import at.hannibal2.skyhanni.discord.CommandInfo
import at.hannibal2.skyhanni.discord.CommandOption
import at.hannibal2.skyhanni.discord.handlers.Commands.commands
import at.hannibal2.skyhanni.discord.util.EventUtil.reply
import at.hannibal2.skyhanni.discord.util.Util.hasPermissions
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import java.awt.Color
import java.time.Instant
import kotlin.collections.List

class Help : Command {
    override val category = "Information"
    override val permissions = "User"
    override val info = CommandInfo(
        name = "help",
        description = "Get help for all OR one specific command.",
        options = listOf(
            CommandOption(
                name = "command",
                description = "Command you want to get help for.",
                required = false
            ),
        )
    )

    override fun execute(event: MessageReceivedEvent, args: List<String>) {
        if (args.isNotEmpty()) {
            val commandName = args[0]
            val command = commands[commandName] ?: return

            if (command.category == "Admin" && !event.hasPermissions()) return

            val data = MessageCreateData.fromEmbeds(event.createHelpEmbed(commandName, command))

            event.reply(data = data)
        } else {
            val commandList = commands.keys.joinToString(", ", prefix = "!")

            event.reply("The bot currently supports these commands: $commandList")
            return
        }
    }

    private fun MessageReceivedEvent.createHelpEmbed(commandName: String, command: Command): MessageEmbed {
        val name = commandName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val description = command.info.description
        val category = command.category
        val user = this.member?.user

        return EmbedBuilder()
            .setAuthor(this.guild.name, "https://github.com/hannibal002/SkyHanni", this.guild.iconUrl)
            .setTitle("\uD83C\uDFF7" + name)
            .setColor(Color.GREEN)
            .setDescription("📄 Description: **$description**\n📁 Category: **$category**")
            .setFooter(user?.name, user?.avatarUrl)
            .setTimestamp(Instant.now())
            .build()
    }
}