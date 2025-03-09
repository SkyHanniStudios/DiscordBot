package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.hasAdminPermissions
import at.hannibal2.skyhanni.discord.Utils.inBotCommandChannel
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.command.BaseCommand
import at.hannibal2.skyhanni.discord.command.PullRequestCommand
import at.hannibal2.skyhanni.discord.command.ServerCommands
import at.hannibal2.skyhanni.discord.command.TagCommands
import at.hannibal2.skyhanni.discord.command.HelpCommand
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.reflections.Reflections
import java.lang.reflect.Modifier

object CommandListener {
    private const val BOT_ID = "1343351725381128193"

    private val commands = mutableMapOf<String, BaseCommand>()
    private val commandsAliases = mutableMapOf<String, BaseCommand>()

    fun getCommands(): Collection<BaseCommand> = commands.values

    fun init() {
        loadCommands()
        loadAliases()
        BOT.jda.getGuildById(BOT.config.allowedServerId)?.let { createCommands(it) }
    }

    fun onMessage(bot: DiscordBot, event: MessageReceivedEvent) {
        event.onMessage(bot)
    }

    fun onInteraction(bot: DiscordBot, event: SlashCommandInteractionEvent) {
        event.onInteraction(bot)
    }

    fun onAutocomplete(event: CommandAutoCompleteInteractionEvent) {
        event.onCompletion()
    }

    private fun MessageReceivedEvent.onMessage(bot: DiscordBot) {
        val message = message.contentRaw.trim()
        if (!isFromGuild) {
            logAction("private dm: '$message'", this)
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

        if (ServerCommands.isKnownServerUrl(this, message)) return
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
            if (!hasAdminPermissions(this)) {
                reply("No permissions $PLEADING_FACE", this)
                return
            }

            if (!inBotCommandChannel(this)) {
                reply("Wrong channel $PLEADING_FACE", this)
                return
            }
        }

        // allows to use `!<command> -help` instead of `!help -<command>`
        if (args.size == 1) {
            if (args.first() == "-help") {
                with(HelpCommand) {
                    this.sendUsageReply(literal, this@onMessage)
                }
                return
            }
        }
        try {
            with(command) {
                execute(args, this@onMessage)
            }
        } catch (e: Exception) {
            reply("Error: ${e.message}", this)
        }
    }

    private fun SlashCommandInteractionEvent.onInteraction(bot: DiscordBot) {
        if (guild?.id != bot.config.allowedServerId || this.user.isBot) return

        val command = getCommand(this.fullCommandName) ?: return

        if (!command.userCommand) {
            if (!hasAdminPermissions(this)) {
                reply("No permissions $PLEADING_FACE")
                return
            }

            if (!inBotCommandChannel(this)) {
                reply("Wrong channel $PLEADING_FACE")
                return
            }
        }

        try {
            with(command) {
                execute(listOf(), this@onInteraction)
            }
        } catch (e: Exception) {
            reply("Error: ${e.message}")
        }
    }

    private fun CommandAutoCompleteInteractionEvent.onCompletion() {
        when (fullCommandName) {
            "help" -> {
                if (focusedOption.name != "command") return

                replyChoiceStrings(
                    commands.filterKeys { key -> key.startsWith(focusedOption.value) }.keys
                ).queue()
            }

            "server" -> {
                if (focusedOption.name != "keyword") return

                replyChoiceStrings(
                    ServerCommands.servers.filter { it.name.startsWith(focusedOption.value, true) }
                        .map { it.name }
                        .take(25)
                ).queue()
            }
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

    fun createCommands(guild: Guild) {
        guild.retrieveCommands().queue {
            val commandData = commands.values.map { value -> convertToData(value) }

            guild.updateCommands().addCommands(commandData).queue()
        }
    }

    private fun convertToData(old: BaseCommand): SlashCommandData {
        return Commands.slash(old.name, old.description).apply {
            old.options.forEach { option ->
                addOption(
                    option.type,
                    option.name.replace(" ", "_"),
                    option.description,
                    option.required,
                    option.autoComplete
                )
            }
        }
    }
}

open class Option(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val type: OptionType = OptionType.STRING,
    val autoComplete: Boolean = false
)