package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

@Suppress("UNUSED_PARAMETER")
class Commands(val config: BotConfig) {

    private val botId = "1343351725381128193"

    private val commands = mutableSetOf<Command>()

    private var tagCommands = TagCommands(config, this)

    init {
        add(Command("help") { event, args -> event.helpCommand(args) })
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
        tagCommands.lastMessages.remove(autor.id)

        val content = event.message.contentRaw.trim()
        if (!content.startsWith("!")) return

        val args = content.substring(1).split(" ", limit = 3)
        val literal = args[0].lowercase()

        val command = commands.find { it.name == literal } ?: run {
            tagCommands.handleTag(event)
            return
        }
        if (command.adminOnly) {
            if (!event.hasAdminPermissions()) {
                event.reply("No permissions \uD83E\uDD7A")
                return
            }
        }
        command.consumer(event, args)
    }

    private fun MessageReceivedEvent.helpCommand(args: List<String>) {
        val hasAdminPerms = hasAdminPermissions()
        val list = commands.filter { !it.adminOnly || hasAdminPerms }.joinToString(", !", prefix = "!") { it.name }
        reply("Supported commands: $list")
    }

    private fun MessageReceivedEvent.hasAdminPermissions(): Boolean {
        val member = member ?: return false
        val allowedRoleIds = config.editPermissionRoleIds.values
        return !member.roles.none { it.id in allowedRoleIds }
    }
}

class Command(
    val name: String,
    val adminOnly: Boolean = false,
    val consumer: (MessageReceivedEvent, List<String>) -> Unit,
)