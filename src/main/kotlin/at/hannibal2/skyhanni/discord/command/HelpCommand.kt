package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.CommandListener
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.checkCommandPermissions
import at.hannibal2.skyhanni.discord.Utils.hasStaffRole
import at.hannibal2.skyhanni.discord.Utils.isStaffCommandChannel
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
    override val permission = Permission.USER
    override val options: List<Option> =
        listOf(Option("command", "Command you want to get help for.", required = false))

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (args.size > 1) return reply("Usage: !help <command>")

        if (args.size == 1) {
            sendUsageReply(args.first().lowercase())
            return
        }

        val permissions = Permission.entries.filter { permission ->
            permission.staffRole?.let { staffRole ->
                hasStaffRole(staffRole)
            } ?: true
        }

        val inStaffCommandChannel = isStaffCommandChannel()
        val commands = CommandListener.commands.filter { command ->
            val hasPerms = if (command.permission != Permission.USER) {
                command.permission in permissions
            } else true
            val correctChannel = if (!inStaffCommandChannel) {
                !command.onlyInStaffCommandChannel
            } else true
            hasPerms && correctChannel
        }

        val list = commands.joinToString(", !", prefix = "!") { it.name }
        reply("Supported commands: $list")

        if (!inStaffCommandChannel && hasStaffRole(StaffRole.COMMUNITY_HELPER)) {
            val id = BOT.config.botCommandChannelId
            val botCommandChannel = "https://discord.com/channels/$id/$id"
            replyWithConsumer("You wanna see the cool staff commands? visit $botCommandChannel") { consumer ->
                runDelayed("staff only command tip deletion", 3.seconds) {
                    consumer.message.messageDelete()
                }
            }
        }
    }


    fun MessageReceivedEvent.sendUsageReply(commandName: String) {
        val command = CommandListener.getCommand(commandName) ?: run {
            reply("Unknown command `!$commandName` $PLEADING_FACE")
            return
        }
        if (!checkCommandPermissions(command)) return
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