package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.getLink
import at.hannibal2.skyhanni.discord.Utils.hasAdminPermissions
import at.hannibal2.skyhanni.discord.Utils.inBotCommandChannel
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.command.*
import at.hannibal2.skyhanni.discord.utils.ErrorManager.handleError
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.reflections.Reflections
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier

object CommandListener {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

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
        // blocking a private dm
        if (!isFromGuild) return
        if (guild.id != bot.config.allowedServerId) return

        if (this.author.isBot) return

        val message = message.contentRaw.trim()
        // empty without the message content intent, and an empty message is never a command
        if (message.isEmpty()) return

        if (TagUndo.getAllNames().none { "!$it" == message }) {
            TagCommands.lastMessages.remove(this.author.id)
        }

        if (ServerCommands.isKnownServerUrl(this, message)) return
        if (PullRequestCommand.isPullRequest(this, message)) return
        if (ModChecker.isModList(this, message)) return
        PleadReactor.doPleadReact(this, message)

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

        runCommand(command, MessageCommandEvent(this), literal, args)
    }

    fun onSlashCommand(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: return
        if (guild.id != BOT.config.allowedServerId) return

        val command = getCommand(event.name) ?: run {
            event.reply("Unknown command $PLEADING_FACE").setEphemeral(true).queue()
            return
        }

        // acknowledges the interaction, we have 3 seconds for this and 15 minutes for the real answer
        event.deferReply().queue()

        val args = command.options.mapNotNull { event.getOption(it.name)?.asString }
        runCommand(command, SlashCommandEvent(event), event.name, args)
    }

    private fun runCommand(command: BaseCommand, event: CommandEvent, literal: String, args: List<String>) {
        if (!command.userCommand) {
            if (!event.hasAdminPermissions()) {
                event.reply("No permissions $PLEADING_FACE")
                return
            }

            if (!event.inBotCommandChannel()) {
                event.reply("Wrong channel $PLEADING_FACE")
                return
            }
        }

        // allows to use `!<command> -help` instead of `!help -<command>`
        if (args.size == 1 && args.first() == "-help") {
            with(HelpCommand) {
                event.sendUsageReply(literal)
            }
            return
        }
        try {
            with(command) {
                event.execute(args)
            }
        } catch (e: Exception) {
            event.reply("Error: ${e.message}")
            e.handleError(
                "Discord command: `${command.name}`",
                "Started at: ${event.message?.getLink() ?: "slash command"}",
            )
        }
    }

    fun registerSlashCommands(jda: JDA) {
        val guild = jda.getGuildById(BOT.config.allowedServerId) ?: run {
            logger.error("Could not register slash commands, guild ${BOT.config.allowedServerId} not found")
            return
        }

        val data = commands.filter { it.supportsSlash }.flatMap { command ->
            command.getAllNames().map { name ->
                Commands.slash(name, command.description).addOptions(
                    command.options.map { option ->
                        OptionData(OptionType.STRING, option.name, option.description, option.required)
                    },
                )
            }
        }

        guild.updateCommands().addCommands(data).queue {
            logger.info("Registered ${data.size} slash commands")
        }

        // commands registered globally in the past would show up as duplicates, but global updates
        // have a much stricter rate limit than guild ones, so only clear when there is something to clear
        jda.retrieveCommands().queue { globalCommands ->
            if (globalCommands.isEmpty()) return@queue
            logger.info("Clearing ${globalCommands.size} stale global slash commands")
            jda.updateCommands().queue()
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

        val aliasCount = commandsMap.size - commands.size
        logger.info(
            "Loaded ${commands.size} commands and $aliasCount aliases, " +
                    "${commandsMap.size} slash command slots needed (limit is 100)",
        )
    }
}

data class Option(val name: String, val description: String, val required: Boolean = true)