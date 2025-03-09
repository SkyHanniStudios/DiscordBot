package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.CommandListener
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.doWhen
import at.hannibal2.skyhanni.discord.Utils.hasAdminPermissions
import at.hannibal2.skyhanni.discord.Utils.inBotCommandChannel
import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.runDelayed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import java.awt.Color
import kotlin.time.Duration.Companion.seconds

object HelpCommand : BaseCommand() {
    override val name: String = "help"
    override val description: String = "Get help for all OR one specific command."
    override val options: List<Option> =
        listOf(Option("command", "Command you want to get help for.", required = false, autoComplete = true))

    override val userCommand: Boolean = true

    override fun execute(args: List<String>, event: Any) {
        if (args.size > 1) {
            reply("Usage: !help <command>", event)
            return
        }

        val command = doWhen(
            event,
            { if (args.isEmpty()) null else args.first().lowercase() },
            { it.getOption("command")?.asString }
        )

        if (command != null) {
            sendUsageReply(command, event)
        } else {
            val commands = if (hasAdminPermissions(event) && inBotCommandChannel(event)) {
                CommandListener.getCommands()
            } else {
                CommandListener.getCommands().filter { it.userCommand }
            }
            val list = commands.joinToString(", !", prefix = "!") { it.name }
            reply("Supported commands: $list", event)

            if (hasAdminPermissions(event) && !inBotCommandChannel(event)) {
                val id = BOT.config.botCommandChannelId
                val botCommandChannel = "https://discord.com/channels/$id/$id"

                doWhen(
                    event, {
                        it.replyWithConsumer("You wanna see the cool admin only commands? visit $botCommandChannel") { consumer ->
                            runDelayed(3.seconds) {
                                consumer.message.messageDelete()
                            }
                        }
                    }, {
                        it.channel.sendMessage("You wanna see the cool admin only commands? visit $botCommandChannel")
                            .queue {
                                runDelayed(3.seconds) {
                                    it.channel.deleteMessageById(it.id).queue()
                                }
                            }
                    }
                )
            }
        }
    }

    fun sendUsageReply(commandName: String, event: Any) {
        val command = CommandListener.getCommand(commandName) ?: run {
            reply("Unknown command `!$commandName` $PLEADING_FACE", event)
            return
        }

        if (!command.userCommand && !hasAdminPermissions(event)) {
            reply("No permissions for command `!$commandName` $PLEADING_FACE", event)
            return
        }

        reply(createHelpEmbed(commandName), event)
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

