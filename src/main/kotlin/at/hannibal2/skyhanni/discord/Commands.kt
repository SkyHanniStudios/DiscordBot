package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.runDelayed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.awt.Color
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@Suppress("UNUSED_PARAMETER")
class Commands(private val config: BotConfig) {

    private val botId = "1343351725381128193"

    private val commands = mutableSetOf<Command>()

    private var tagCommands = TagCommands(config, this)
    private var serverCommands = ServerCommands(config, this)
    private var pullRequestCommands = PullRequestCommands(config, this)

    init {
        add(Command("help", userCommand = true) { event, args -> event.helpCommand(args) })
    }

    fun add(element: Command) {
        commands.add(element)
    }

    fun onMessage(bot: DiscordBot, event: MessageReceivedEvent) {
        if (event.guild.id != bot.config.allowedServerId) return

        val author = event.author
        if (author.isBot) {
            if (author.id == botId) {
                BotMessageHandler.handle(event)
            }
            return
        }
        val content = event.message.contentRaw.trim()
        if (content != "!undo") {
            tagCommands.lastMessages.remove(author.id)
        }

        if (!isCommand(content)) return

        val args = content.substring(1).split(" ")
        val literal = args[0].lowercase()

        val command = commands.find { it.name == literal } ?: run {
            tagCommands.handleTag(event)
            return
        }

        if (!command.userCommand) {
            if (!event.hasAdminPermissions()) {
                event.reply("No permissions \uD83E\uDD7A")
                return
            }

            if (!event.inBotCommandChannel()) {
                event.reply("Wrong channel \uD83E\uDD7A")
                return
            }
        }

        command.consumer(event, args)
    }

    private val commandPattern = "^!(?!!).+".toPattern()

    // ensures the command starts with ! while ignoring !!
    private fun isCommand(message: String): Boolean {
        return commandPattern.matcher(message).matches()
    }

    private fun MessageReceivedEvent.inBotCommandChannel() = channel.id == config.botCommandChannelId

    private fun MessageReceivedEvent.helpCommand(args: List<String>) {
        val hasAdminPerms = hasAdminPermissions()

        if (args.size >= 2) {
            val commandName = args[1].lowercase()
            val command = CommandsData.getCommand(commandName) ?: return

            if (!command.userCommand && !hasAdminPerms) return

            val embed = this.createHelpEmbed(commandName, command)

            this.reply(embed)
        } else {
            val commands = if (hasAdminPerms && inBotCommandChannel()) {
                commands
            } else {
                commands.filter { it.userCommand }
            }
            val list = commands.joinToString(", !", prefix = "!") { it.name }
            reply("Supported commands: $list")

            if (hasAdminPerms && !inBotCommandChannel()) {
                val id = config.botCommandChannelId
                val botCommandChannel = "https://discord.com/channels/$id/$id"
                replyWithConsumer("You wanna see the cool admin only commands? visit $botCommandChannel") { consumer ->
                    runDelayed(3.seconds) {
                        consumer.message.messageDelete()
                    }
                }
            }
        }
    }

    private fun MessageReceivedEvent.hasAdminPermissions(): Boolean {
        val member = member ?: return false
        val allowedRoleIds = config.editPermissionRoleIds.values
        return !member.roles.none { it.id in allowedRoleIds }
    }

    fun existCommand(text: String): Boolean = commands.find { it.name.equals(text, ignoreCase = true) } != null

    private fun MessageReceivedEvent.createHelpEmbed(commandName: String, command: CommandData): MessageEmbed {
        val name = commandName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val description = command.description
        val options = command.options.joinToString("\n")

        val em = EmbedBuilder()

        em.setTitle(description)
        em.setColor(Color.GREEN)

        for (option in command.options) {
            em.addField(option.name, option.description, true)
            em.addField("Required", if (option.required) "✅" else "❌", true)
            em.addBlankField(true)
        }

        return em.build()
//        val category = command.category 📁 Category: **$category**
    }
}

class Command(
    val name: String,
    val userCommand: Boolean = false,
    val consumer: (MessageReceivedEvent, List<String>) -> Unit,
)