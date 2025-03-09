package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.*
import at.hannibal2.skyhanni.discord.Utils.hasAdminPermissions
import at.hannibal2.skyhanni.discord.Utils.inBotCommandChannel
import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.runDelayed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.awt.Color
import kotlin.time.Duration.Companion.seconds

object HelpCommand : BaseCommand() {
    override val name: String = "help"
    override val description: String = "Get help for all OR one specific command."
    override val options: List<Option> =
        listOf(Option("command", "Command you want to get help for.", required = false))

    override val userCommand: Boolean = true

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (args.size > 1) {
            reply("Usage: !help <command>")
            return
        }

        if (args.size == 1) {
            sendUsageReply(args.first().lowercase())
        } else {
            val commands = if (hasAdminPermissions() && inBotCommandChannel()) {
                CommandListener.getCommands()
            } else {
                CommandListener.getCommands().filter { it.userCommand }
            }
            val list = commands.joinToString(", !", prefix = "!") { it.name }
            reply("Supported commands: $list")

            if (hasAdminPermissions() && !inBotCommandChannel()) {
                val id = BOT.config.botCommandChannelId
                val botCommandChannel = "https://discord.com/channels/$id/$id"
                replyWithConsumer("You wanna see the cool admin only commands? visit $botCommandChannel") { consumer ->
                    runDelayed(3.seconds) {
                        consumer.message.messageDelete()
                    }
                }
            }
        }
    }


    fun MessageReceivedEvent.sendUsageReply(commandName: String) {
        val command = CommandListener.getCommand(commandName) ?: run {
            reply("Unknown command `!$commandName` $PLEADING_FACE")
            return
        }

        if (!command.userCommand && !hasAdminPermissions()) {
            reply("No permissions for command `!$commandName` $PLEADING_FACE")
            return
        }

        this.reply(command.createHelpEmbed(commandName))
    }


    private fun BaseCommand.createHelpEmbed(commandName: String): MessageEmbed {
        val em = EmbedBuilder()

        em.setTitle("Usage: /$commandName <" + this.options.joinToString("> <") { it.name } + ">")
        em.setDescription("📋 **${this.description}**")
        em.setColor(Color.GREEN)

        for (option in this.options) {
            em.addField(option.name, option.description, true)
            em.addField("Required", if (option.required) "✅" else "❌", true)
            em.addBlankField(true)
        }

        return em.build()
    }
}