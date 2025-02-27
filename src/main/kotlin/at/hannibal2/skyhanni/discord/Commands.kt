package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.runDelayed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import kotlin.time.Duration.Companion.seconds

@Suppress("UNUSED_PARAMETER")
class Commands(private val config: BotConfig) {

    private val botId = "1343351725381128193"

    private val commands = mutableSetOf<Command>()

    private var tagCommands = TagCommands(config, this)
    private var serverCommands = ServerCommands(config, this)

    init {
        add(Command("help", userCommand = true) { event, args -> event.helpCommand(args) })
    }

    fun add(element: Command) {
        commands.add(element)
    }

    fun onMessage(bot: DiscordBot, event: MessageReceivedEvent) {
        if (event.guild.id != bot.config.allowedServerId) return

        val autor = event.author
        if (autor.isBot) {
            if (autor.id == botId) {
                BotMessageHandler.handle(event)
            }
            return
        }
        val content = event.message.contentRaw.trim()
        if (content != "!undo") {
            tagCommands.lastMessages.remove(autor.id)
        }

        if (!content.startsWith("!")) return

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

    private fun MessageReceivedEvent.inBotCommandChannel() = channel.id == config.botCommandChannelId

    private fun MessageReceivedEvent.helpCommand(args: List<String>) {
        val hasAdminPerms = hasAdminPermissions()
        val commands = if (hasAdminPerms && inBotCommandChannel()) {
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

    private fun MessageReceivedEvent.hasAdminPermissions(): Boolean {
        val member = member ?: return false
        val allowedRoleIds = config.editPermissionRoleIds.values
        return !member.roles.none { it.id in allowedRoleIds }
    }
}

class Command(
    val name: String,
    val userCommand: Boolean = false,
    val consumer: (MessageReceivedEvent, List<String>) -> Unit,
)