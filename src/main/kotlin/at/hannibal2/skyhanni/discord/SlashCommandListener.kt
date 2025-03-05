package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.createHelpEmbed
import at.hannibal2.skyhanni.discord.Utils.runDelayed
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import kotlin.time.Duration.Companion.seconds

class SlashCommandListener(private val config: BotConfig) {

    private val commands = mutableSetOf<SlashCommand>()
    private val commandsData = mutableSetOf<Command>()

    init {
        ServerSlashCommands(config, this)
        add(SlashCommand("help", userCommand = true) { event -> event.helpCommand() })
    }

    fun add(element: SlashCommand) {
        commands.add(element)
    }

    fun onCommand(event: SlashCommandInteractionEvent) {
        val command = commands.firstOrNull { it.name == event.fullCommandName } ?: return

        command.consumer(event)
    }

    fun onAutocomplete(event: CommandAutoCompleteInteractionEvent) {
        when (event.fullCommandName) {
            "help" -> {
                if (event.focusedOption.name != "command") return

                event.replyChoiceStrings(
                    CommandsData.getCommands()
                        .filterKeys { key -> key.startsWith(event.focusedOption.value) }.keys
                ).queue()
            }

            "server" -> {
                if (event.focusedOption.name != "keyword") return

                event.replyChoiceStrings(Database.listServers().map { it.keyword }
                    .filter { it.startsWith(event.focusedOption.value) }).queue()
            }
        }
    }

    private fun SlashCommandInteractionEvent.inBotCommandChannel() = channel.id == config.botCommandChannelId

    private fun SlashCommandInteractionEvent.helpCommand() {
        getOption("command")?.let { sendUsageReply(it.asString) } ?: run {
            val commands = if (hasAdminPermissions() && inBotCommandChannel()) {
                commands
            } else {
                commands.filter { it.userCommand }
            }
            val list = commands.joinToString(", !", prefix = "!") { it.name }
            reply("Supported commands: $list").queue()

            if (hasAdminPermissions() && !inBotCommandChannel()) {
                val id = config.botCommandChannelId
                val botCommandChannel = "https://discord.com/channels/$id/$id"
                reply("You wanna see the cool admin only commands? visit $botCommandChannel").queue()
            }
        }
    }

    private fun SlashCommandInteractionEvent.sendUsageReply(commandName: String) {
        val command = CommandsData.getCommand(commandName) ?: run {
            reply("Unknown command `!$commandName` \uD83E\uDD7A")
            return
        }

        if (!command.userCommand && !hasAdminPermissions()) {
            reply("No permissions for command `!$commandName` \uD83E\uDD7A")
            return
        }

        replyEmbeds(command.createHelpEmbed(commandName))
            .setEphemeral(true)
            .queue()
    }

    private fun SlashCommandInteractionEvent.hasAdminPermissions(): Boolean {
        val member = member ?: return false
        val allowedRoleIds = config.editPermissionRoleIds.values
        return !member.roles.none { it.id in allowedRoleIds }
    }

    fun deleteSlashCommand(name: String, guild: Guild) {
        val command = commandsData.firstOrNull { it.name.equals(name, true) } ?: return
        guild.deleteCommandById(command.id).queue()
    }

    fun updateSlashCommand(name: String, guild: Guild) {
        commandsData.firstOrNull { it.name.equals(name, true) } ?: return
        val data = CommandsData.getCommand(name) ?: return
        guild.upsertCommand(convertToData(data)).queue()
    }

    fun createSlashCommands(guild: Guild) {
        guild.retrieveCommands().queue { current ->
            val commandData = CommandsData.getCommands()
                .filterKeys { key -> current.none { it.name == key } }.values.map { value -> convertToData(value) }

            runDelayed(1.seconds) {
                for (command in commandData) {
                    guild.upsertCommand(command).queue()
                    println("added ${command.name}")
                    Thread.sleep(5000)
                }
            }
        }

        runDelayed(1.seconds)
        {
            guild.retrieveCommands().queue { commandsData.addAll(it) }
        }
    }

    private fun convertToData(old: CommandData): SlashCommandData {
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

class SlashCommand(
    val name: String,
    val userCommand: Boolean = false,
    val consumer: (SlashCommandInteractionEvent) -> Unit,
)