package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.getLink
import at.hannibal2.skyhanni.discord.Utils.hasAdminPermissions
import at.hannibal2.skyhanni.discord.Utils.inBotCommandChannel
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.command.*
import at.hannibal2.skyhanni.discord.utils.ErrorManager.handleError
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.reflections.Reflections
import java.lang.reflect.Modifier

object CommandListener {
    private const val BOT_ID = "1343351725381128193"

    var commands = listOf<BaseCommand>()
        private set
    private var commandsMap = mapOf<String, BaseCommand>()

    fun init() {
        loadCommands()
    }

    fun onMessage(bot: DiscordBot, event: MessageReceivedEvent) {
        event.onMessage(bot)
    }

    private fun MessageReceivedEvent.onMessage(bot: DiscordBot) {
        val message = message.contentRaw.trim()
        if (!isFromGuild) {
            logAction("private dm: '$message'")
            return
        }
        if (guild.id != bot.config.allowedServerId) return

        if (this.author.isBot) {
            if (this.author.id == BOT_ID) {
                BotMessageHandler.handle(this)
            }
            return
        }

        if (TagUndo.getAllNames().none { "!$it" == message }) {
            TagCommands.lastMessages.remove(this.author.id)
        }

        if (ServerCommands.isKnownServerUrl(this, message)) return
        if (PullRequestCommand.isPullRequest(this, message)) return
        if (ModChecker.isModList(this, message)) return

        var commandMessage = message
        // ! pr arg -> !pr arg
        while (commandMessage.startsWith("! ")) {
            commandMessage = commandMessage.replaceFirst("! ", "!")
        }

        if (!isCommand(commandMessage)) return

        val split = commandMessage.substring(1).split(" ")
        val literal = split.first().lowercase()
        val args = split.drop(1)

        val command = getCommand(literal) ?: run {
            TagCommands.handleTag(this)
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
        if (args.size == 1 && args.first() == "-help") {
            with(HelpCommand) {
                sendUsageReply(literal)
            }
            return
        }
        try {
            with(command) {
                execute(args)
            }
        } catch (e: Exception) {
            reply("Error: ${e.message}")
            e.handleError(
                "Discord command: `${command.name}`",
                "Started at: ${getMessage().getLink()}",
            )
        }
    }

    private val commandPattern = "^!(?!!)[\\s\\S]+".toPattern()

    // ensures the command starts with ! while ignoring !!
    private fun isCommand(message: String): Boolean = commandPattern.matcher(message).matches()

    fun getCommand(name: String): BaseCommand? = commandsMap[name]

    fun existsCommand(name: String): Boolean = name in commandsMap

    private fun loadCommands() {
        val reflections = Reflections("at.hannibal2")
        val classes: Set<Class<out BaseCommand>> = reflections.getSubTypesOf(BaseCommand::class.java)
        val commands = mutableListOf<BaseCommand>()
        val commandsMap = mutableMapOf<String, BaseCommand>()
        for (clazz in classes) {
            try {
                if (Modifier.isAbstract(clazz.modifiers)) continue
                val command = clazz.kotlin.objectInstance ?: clazz.getConstructor().newInstance()

                for (name in command.getAllNames()) {
                    require(name !in commandsMap) { "Duplicate command name/alias: $name" }
                    commandsMap[name] = command
                }
                commands.add(command)
            } catch (e: Exception) {
                e.handleError(
                    "in loadCommands!",
                    "class name: ${clazz.name}",
                )
            }
        }
        this.commands = commands
        this.commandsMap = commandsMap
    }
}

data class Option(val name: String, val description: String, val required: Boolean = true)