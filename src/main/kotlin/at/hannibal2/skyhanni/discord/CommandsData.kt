package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.interactions.commands.OptionType

object CommandsData {

    private val commands = listOf(
        CommandData(
            name = "help",
            description = "Get help for all OR one specific command.",
            options = listOf(
                Option(
                    "command",
                    "Command you want to get help for.",
                    required = false,
                    autoComplete = true
                )
            ),
            userCommand = true
        ),
        CommandData(
            name = "pr",
            description = "Displays useful information about a pull request on GitHub.",
            options = listOf(
                Option(
                    "number",
                    "Number of the pull request you want to display.",
                    type = OptionType.NUMBER
                )
            ),
        ),
        CommandData(
            name = "server",
            description = "Displays information about a server from our 'useful server list'.",
            options = listOf(
                Option("keyword", "Keyword of the server you want to display.", autoComplete = true),
                Option(
                    "debug",
                    "Display even more useful information (-d to use).",
                    required = false,
                    type = OptionType.BOOLEAN
                )
            ),
            userCommand = true
        ),
        CommandData(
            name = "serverlist",
            description = "Displays all servers in the database.",
            aliases = listOf("servers")
        ),
        CommandData(
            name = "serveradd",
            description = "Adds a server to the database.",
            options = listOf(
                Option("keyword", "Keyword of the server you want to add."),
                Option("display name", "Display name of the server."),
                Option("invite link", "Invite link of the server."),
                Option("description", "Description of the server.")
            )
        ),
        CommandData(
            name = "serveredit",
            description = "Edit a server from the database.",
            options = listOf(
                Option("keyword", "Keyword of the server you want to edit."),
                Option("display name", "Display name of the server."),
                Option("invite link", "Invite link of the server."),
                Option("description", "Description of the server.")
            )
        ),
        CommandData(
            name = "serveraddalias",
            description = "Adds an alias to a server in the database.",
            options = listOf(
                Option("keyword", "Keyword of the server you want to add the alias to."),
                Option("alias", "The alias you want to add.")
            )
        ),
        CommandData(
            name = "serveraliasdelete",
            description = "Deletes an alias from a server in the database.",
            options = listOf(
                Option("keyword", "Keyword of the server you want to delete the alias from."),
                Option("alias", "The alias you want to delete.")
            )
        ),
        CommandData(
            name = "serverdelete",
            description = "Deletes a server from the database.",
            options = listOf(Option("keyword", "Keyword of the server you want to delete."))
        ),
        CommandData(
            name = "taglist",
            description = "Lists all available tags.",
            aliases = listOf("tags"),
            userCommand = true
        ),
        CommandData(
            name = "tagedit",
            description = "Edits a tag in the database.",
            options = listOf(
                Option("tag", "The tag you want to edit.", autoComplete = true),
                Option("response", "Response you want the tag to have.")
            ),
            aliases = listOf("tagchange")
        ),
        CommandData(
            name = "tageditlast",
            description = "Show info on how to edit the last tag used."
        ),
        CommandData(
            name = "tagadd",
            description = "Adds a tag to the database.",
            options = listOf(
                Option("keyword", "Keyword you want the tag to have."),
                Option("response", "Response you want the tag to have.")
            ),
            aliases = listOf("tagcreate")
        ),
        CommandData(
            name = "tagdelete",
            description = "Deletes a tag from the database.",
            options = listOf(Option("keyword", "Keyword of the tag you want to delete.", autoComplete = true)),
            aliases = listOf("tagremove")
        ),
        CommandData(
            name = "undo",
            description = "Undoes something not quite sure."
        )
    )

    private val commandMap = commands.associateBy { it.name } +
            commands.flatMap { cmd -> cmd.aliases.map { alias -> alias to cmd } }

    fun getCommand(nameOrAlias: String): CommandData? {
        return commandMap[nameOrAlias]
    }

    fun getCommands(): Map<String, CommandData> =
        commandMap.filterKeys { it !in commands.flatMap { cmd -> cmd.aliases } }
}

data class CommandData(
    val name: String,
    val description: String,
    val options: List<Option> = emptyList(),
    val aliases: List<String> = emptyList(),
    val userCommand: Boolean = false
)

data class Option(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val type: OptionType = OptionType.STRING,
    val autoComplete: Boolean = false
)

