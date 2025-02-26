package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

@Suppress("UNUSED_PARAMETER")
class Commands(config: BotConfig) {

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

        if (event.author.isBot) {
            if (event.author.id == botId) {
                BotMessageHandler.handle(event)
            }
            return
        }

        val content = event.message.contentRaw.trim()
        if (!content.startsWith("!")) return

        val args = content.substring(1).split(" ", limit = 3)
        val literal = args[0].lowercase()

        (commands.find { it.name == literal } ?: run {
            tagCommands.handleTag(event)
            return
        }).consumer(event, args)
    }

    private fun MessageReceivedEvent.helpCommand(args: List<String>) {
        reply("Supported commands: !help, !add, !edit, !delete/!remove, !list/!taglist")
    }
}

class Command(val name: String, val consumer: (MessageReceivedEvent, List<String>) -> Unit)