package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.hasAdminPermissions
import at.hannibal2.skyhanni.discord.Utils.inBotCommandChannel
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.command.BaseCommand
import at.hannibal2.skyhanni.discord.command.HelpCommand
import at.hannibal2.skyhanni.discord.command.PullRequestCommand
import at.hannibal2.skyhanni.discord.command.TagCommands
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.reflections.Reflections
import java.lang.reflect.Modifier

object CommandListener {
    private const val BOT_ID = "1343351725381128193"

    private val commands = mutableMapOf<String, BaseCommand>()
    private val commandsAliases = mutableMapOf<String, BaseCommand>()

    fun getCommands(): Collection<BaseCommand> = commands.values

    private val serversCommands = ServerCommands(this)

    fun init() {
        loadCommands()
        loadAliases()
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
        if (message != "!undo") {
            TagCommands.lastMessages.remove(this.author.id)
        }

        if (serversCommands.isKnownServerUrl(this, message)) return
        if (PullRequestCommand.isPullRequest(this, message)) return

        if (!isCommand(message)) return

        val split = message.substring(1).split(" ")
        val literal = split[0].lowercase()
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
        if (args.size == 1) {
            if (args.first() == "-help") {
                with(HelpCommand) {
                    this@onMessage.sendUsageReply(literal)
                }
                return
            }
        }
        try {
            with(command) {
                this@onMessage.execute(args)
            }
        } catch (e: Exception) {
            reply("Error: ${e.message}")
        }
    }

    private val commandPattern = "^!(?!!).+".toPattern()

    // ensures the command starts with ! while ignoring !!
    private fun isCommand(message: String): Boolean {
        return commandPattern.matcher(message).matches()
    }

    fun getCommand(name: String): BaseCommand? = commands[name] ?: commandsAliases[name]

    fun existsCommand(name: String): Boolean = getCommand(name) != null

    private fun loadCommands() {
        val reflections = Reflections("at.hannibal2")
        val classes: Set<Class<out BaseCommand>> = reflections.getSubTypesOf(BaseCommand::class.java)
        for (clazz in classes) {
            try {
                if (Modifier.isAbstract(clazz.modifiers)) continue
                val command = clazz.kotlin.objectInstance ?: clazz.getConstructor().newInstance()
                require(command.name !in commands) { "Duplicate command name: ${command.name}" }
                commands[command.name] = command
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun loadAliases() {
        for (command in commands.values) {
            for (alias in command.aliases) {
                require(alias !in commandsAliases) { "Duplicate command alias: $alias" }
                require(alias !in commands) { "Duplicate command alias: $alias" }
                commandsAliases[alias] = command
            }
        }
    }
}

class Command(
    val name: String,
    val userCommand: Boolean = false,
    val consumer: (MessageReceivedEvent, List<String>) -> Unit,
)