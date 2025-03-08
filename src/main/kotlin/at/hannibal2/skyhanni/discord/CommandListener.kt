package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.runDelayed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.awt.Color
import kotlin.time.Duration.Companion.seconds

class CommandListener(private val config: BotConfig) {

    private val botId = "1343351725381128193"

    private val commands = mutableSetOf<Command>()

    private val tagCommands = TagCommands(config, this)
    private val serversCommands = ServerCommands(config, this)
    private val pullRequestCommands = PullRequestCommands(config, this)

    init {
        add(Command("help", userCommand = true) { event, args -> event.helpCommand(args) })
    }

    fun add(element: Command) {
        commands.add(element)
    }

    fun onMessage(bot: DiscordBot, event: MessageReceivedEvent) {
        event.onMessage(bot)
    }

    private fun MessageReceivedEvent.onMessage(bot: DiscordBot) {
        if (guild.id != bot.config.allowedServerId) return

        if (this.author.isBot) {
            if (this.author.id == botId) {
                BotMessageHandler.handle(this)
            }
            return
        }
        val content = message.contentRaw.trim()
        if (content != "!undo") {
            tagCommands.lastMessages.remove(this.author.id)
        }

        if (serversCommands.isKnownServerUrl(this, content)) return
        if (pullRequestCommands.isPullRequest(this, content)) return

        if (!isCommand(content)) return

        val args = content.substring(1).split(" ")
        val literal = args[0].lowercase()

        val command = commands.find { it.name == literal } ?: run {
            tagCommands.handleTag(this)
            return
        }

        if (!command.userCommand) {
            if (!hasAdminPermissions()) {
                reply("No permissions $PLEADING_FACE")
                return
            }

            if (!inBotCommandChannel()) {
                reply("Wrong channel $PLEADING_FACE")
                return
            }
        }

        // allows to use `!<command> -help` instaed of `!help -<command>`
        if (args.size == 2) {
            if (args[1] == "-help") {
                sendUsageReply(literal)
                return
            }
        }
        try {
            command.consumer(this, args)
        } catch (e: Exception) {
            reply("Error: ${e.message}")
        }
    }

    private val commandPattern = "^!(?!!).+".toPattern()

    // ensures the command starts with ! while ignoring !!
    private fun isCommand(message: String): Boolean {
        return commandPattern.matcher(message).matches()
    }

    private fun MessageReceivedEvent.inBotCommandChannel() = channel.id == config.botCommandChannelId

    private fun MessageReceivedEvent.helpCommand(args: List<String>) {
        if (args.size > 2) {
            reply("Usage: !help <command>")
            return
        }

        if (args.size == 2) {
            sendUsageReply(args[1].lowercase())
        } else {
            val commands = if (hasAdminPermissions() && inBotCommandChannel()) {
                commands
            } else {
                commands.filter { it.userCommand }
            }
            val list = commands.joinToString(", !", prefix = "!") { it.name }
            reply("Supported commands: $list")

            if (hasAdminPermissions() && !inBotCommandChannel()) {
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

    private fun MessageReceivedEvent.sendUsageReply(commandName: String) {
        val command = CommandsData.getCommand(commandName) ?: run {
            reply("Unknown command `!$commandName` $PLEADING_FACE")
            return
        }

        if (!command.userCommand && !hasAdminPermissions()) {
            reply("No permissions for command `!$commandName` $PLEADING_FACE")
            return
        }

        this.reply(command.createHelpEmbed(commandName))
    }

    private fun MessageReceivedEvent.hasAdminPermissions(): Boolean {
        val member = member ?: return false
        val allowedRoleIds = config.editPermissionRoleIds.values
        return !member.roles.none { it.id in allowedRoleIds }
    }

    fun existCommand(text: String): Boolean = commands.find { it.name.equals(text, ignoreCase = true) } != null

    private fun CommandData.createHelpEmbed(commandName: String): MessageEmbed {
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

class Command(
    val name: String,
    val userCommand: Boolean = false,
    val consumer: (MessageReceivedEvent, List<String>) -> Unit,
)