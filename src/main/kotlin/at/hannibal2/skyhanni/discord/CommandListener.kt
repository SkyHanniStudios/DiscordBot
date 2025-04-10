package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.hasAdminPermissions
import at.hannibal2.skyhanni.discord.Utils.inBotCommandChannel
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.command.HelpCommand
import at.hannibal2.skyhanni.discord.command.BaseCommand
import at.hannibal2.skyhanni.discord.command.MessageEvent
import at.hannibal2.skyhanni.discord.command.SlashCommandEvent
import at.hannibal2.skyhanni.discord.command.ServerCommands
import at.hannibal2.skyhanni.discord.command.PullRequestCommand.isPullRequest
import at.hannibal2.skyhanni.discord.command.ServerCommands.isKnownServerUrl
import at.hannibal2.skyhanni.discord.command.TagCommands
import at.hannibal2.skyhanni.discord.command.TagCommands.handleTag
import at.hannibal2.skyhanni.discord.command.TagUndo
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.reflections.Reflections
import java.lang.reflect.Modifier

object CommandListener {
    private const val BOT_ID = "1343351725381128193"

    var commands = listOf<BaseCommand>()
        private set
    private var commandsMap = mapOf<String, BaseCommand>()

    fun init() {
        loadCommands()

        BOT.jda.awaitReady()

        BOT.jda.getGuildById(BOT.config.allowedServerId)?.let { createCommands(it) }
    }

    fun onMessage(bot: DiscordBot, event: MessageEvent) {
        event.onMessage(bot)
    }

    fun onInteraction(bot: DiscordBot, event: SlashCommandEvent) {
        event.onInteraction(bot)
    }

    fun onAutocomplete(event: CommandAutoCompleteInteractionEvent) {
        event.onCompletion()
    }

    private fun MessageEvent.onMessage(bot: DiscordBot) {
        val message = message.contentRaw.trim()
        if (!isFromGuild) {
            logAction("private dm: '$message'")
            return
        }
        if (event.guild.id != bot.config.allowedServerId) return

        if (author.isBot) {
            if (author.id == BOT_ID) {
                BotMessageHandler.handle(event)
            }
            return
        }

        if (TagUndo.getAllNames().none { "!$it" == message }) {
            TagCommands.lastMessages.remove(author.id)
        }

        if (isKnownServerUrl(message) || isPullRequest(message) || !isCommand(message)) return

        val split = message.substring(1).split(" ")
        val literal = split.first().lowercase()
        val args = split.drop(1)

        val command = getCommand(literal) ?: run {
            handleTag()
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

        // allows to use `!<command> -help` instead of `!help -<command>`
        if (args.size == 1 && args.first() == "-help") {
            with(HelpCommand) {
                sendUsageReply(literal)
            }
            return
        }
        // allows to use `!<command> -help` instead of `!help -<command>`
        if (args.size == 1) {
            if (args.first() == "-help") {
                with(HelpCommand) {
                    sendUsageReply(literal)
                }
                return
            }
        }
        try {
            with(command) {
                execute(args)
            }
        } catch (e: Exception) {
            reply("Error: ${e.message}")
        }
    }

    private fun SlashCommandEvent.onInteraction(bot: DiscordBot) {
        if (event.guild?.id != bot.config.allowedServerId || author.isBot) return

        val command = getCommand(event.fullCommandName) ?: return

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

        try {
            with(command) {
                execute(listOf())
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
                    commands.filter { it.name.startsWith(focusedOption.value) }
                        .map { it.name }
                        .take(25)
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

            "tagedit" -> {
                if (focusedOption.name != "keyword") return

                replyChoiceStrings(
                    Database.listTags().filter { it.keyword.startsWith(focusedOption.value, true) }.map { it.keyword }
                        .take(25)
                ).queue()
            }

            "tagdelete" -> {
                if (focusedOption.name != "keyword") return

                replyChoiceStrings(
                    Database.listTags().filter { it.keyword.startsWith(focusedOption.value, true) }.map { it.keyword }
                        .take(25)
                ).queue()
            }
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
                e.printStackTrace()
            }
        }
        this.commands = commands
        this.commandsMap = commandsMap
    }

    private fun createCommands(guild: Guild) {
        guild.retrieveCommands().queue {
            val commandData = commands.map { value -> convertToData(value) }

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

data class Option(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val type: OptionType = OptionType.STRING,
    val autoComplete: Boolean = false
)