package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.sendMessageToBotChannel
import at.hannibal2.skyhanni.discord.github.GitHubClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.dv8tion.jda.api.interactions.commands.OptionType
import kotlin.time.Duration.Companion.milliseconds

object ServerCommands {
    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", BOT.config.githubToken)

    data class Server(
        val keyword: String,
        val name: String,
        val invite: String,
        val description: String,
        val aliases: List<String>,
    ) {
        fun print(tutorial: Boolean = false): String = buildString {
            appendLine("**$name**\n")
            if (description.isNotEmpty()) {
                appendLine(description)
            }
            if (!tutorial) {
                append(invite)
            } else {
                append("||In the future, you can do `!server $keyword`. Then you get this auto reply||")
            }
        }

        fun printDebug(): String = buildString {
            append("keyword: '$keyword'\n")
            append("displayName: '$name'\n")
            append("description: '$description'\n")
            append("inviteLink: '<$invite>'\n")
            append("aliases: $aliases\n")
        }
    }

    data class ServerJson(
        val name: String,
        val size: String,
        val invite: String,
        val description: String,
        val aliases: List<String>? = null,
    )

    var servers = listOf<Server>()
        private set
    private val discordServerPattern = "(https?://)?(www\\.)?(discord\\.gg|discord\\.com/invite)/[\\w-]+".toPattern()

    internal fun loadServers(startup: Boolean) {
        val json = github.getFileContent("data/discord_servers.json") ?: error("Error loading discord_servers")

        // Parse JSON as a map of maps.
        val type = object : TypeToken<Map<String, Map<String, ServerJson>>>() {}.type
        val data: Map<String, Map<String, ServerJson>> = Gson().fromJson(json, type)

        // Flatten into a list of Mods (ignoring category)
        servers = data.flatMap { (_, serverCategories) ->
            serverCategories.map { (id, data) ->
                Server(id.lowercase(),
                    data.name,
                    data.invite,
                    data.description,
                    data.aliases?.map { it.lowercase() } ?: emptyList())
            }
        }

        checkForDuplicates(startup)
    }

    private fun checkForDuplicates(startup: Boolean) {
        val keyToServers = mutableMapOf<String, MutableList<Server>>()
        servers.forEach { server ->
            val keys = listOf(server.keyword, server.name) + server.aliases
            keys.forEach { key ->
                keyToServers.getOrPut(key.lowercase()) { mutableListOf() }.add(server)
            }
        }

        val duplicates = mutableSetOf<String>()
        for ((key, serverList) in keyToServers.filter { it.value.size > 1 }) {
            if (serverList.size == 2) {
                val nameA = serverList[0].name
                val nameB = serverList[1].name
                if (nameA == nameB && key == nameA.lowercase()) {
                    // skip if the server name is the same as the key name
                    continue
                }
            }
            duplicates.add("'$key' found in ${serverList.map { it.name }}")
            println("Duplicate key '$key' found in servers: ${serverList.map { it.name }}")
        }
        val count = duplicates.size
        if (count > 0) {
            BOT.logger.warn("$count duplicate servers found!")
            val message = "Found $count duplicate servers:\n${duplicates.joinToString("\n")}"
            if (startup) {
                Utils.runDelayed(500.milliseconds) {
                    sendMessageToBotChannel(message)
                }
            } else {
                sendMessageToBotChannel(message)
            }
        } else {
            BOT.logger.info("no duplicate servers found.")
        }
    }

    internal fun getServer(name: String): Server? {
        for (server in servers) {
            if (server.keyword.equals(name, ignoreCase = true)) {
                return server
            }
            if (server.name.equals(name, ignoreCase = true)) {
                return server
            }
            if (name in server.aliases) {
                return server
            }
        }

        return null
    }

    private fun isDiscordInvite(message: String): Boolean = discordServerPattern.matcher(message).find()

    private fun getServerByInviteUrl(url: String): Server? = servers.firstOrNull { it.invite == url }

    fun MessageEvent.isKnownServerUrl(message: String): Boolean {
        val server = getServerByInviteUrl(message) ?: run {
            if (isDiscordInvite(message)) {
                logAction("sends unknown discord invite '$message'")
            }
            return false
        }

        reply(server.print(tutorial = true))
        return true
    }

    fun listServers(): String {
        if (servers.isEmpty()) return "No servers found."

        val list = servers.joinToString("\n") { server ->
            val aliases = server.aliases
            if (aliases.isNotEmpty()) "${server.keyword} [${aliases.joinToString(", ")}]"
            else server.keyword
        }
        return "Server list:\n$list"
    }
}

@Suppress("unused")
class ServerCommand : BaseCommand() {
    override val name: String = "server"
    override val description: String = "Displays information about a server from our 'useful server list'."
    override val options: List<Option> = listOf(
        Option("keyword", "Keyword of the server you want to display.", autoComplete = true),
        Option(
            "debug",
            "Display even more useful information (-d to use).",
            required = false,
            type = OptionType.BOOLEAN
        )
    )
    override val userCommand: Boolean = true

    override fun CommandEvent.execute(args: List<String>) {
        if (args.isNotEmpty() && args.size !in 1..2) {
            wrongUsage("<keyword>")
            return
        }
        val keyword = doWhen(
            isMessage = { args.first() }, isSlashCommand = { it.getOption("keyword")?.asString }
        ) ?: return

        val debug = doWhen(
            isMessage = { args.getOrNull(1) == "-d" },
            isSlashCommand = { it.getOption("debug")?.asBoolean == true }
        ) ?: false

        val server = ServerCommands.getServer(keyword.lowercase())
        if (server == null) {
            userError("Server with keyword '$keyword' not found.")
            return
        }
        if (debug) reply(server.printDebug())
        else reply(server.print())
    }
}

@Suppress("unused")
class ServerUpdate : BaseCommand() {
    override val name: String = "serverupdate"
    override val description: String = "Updates the server list."
    override val aliases: List<String> = listOf("updateservers")

    override fun CommandEvent.execute(args: List<String>) {
        ServerCommands.loadServers(startup = false)
        reply(
            "Updated server list from [GitHub](<https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/discord_servers.json>)."
        )
        logAction("updated server list from github")
    }
}

@Suppress("unused")
class ServerList : BaseCommand() {
    override val name: String = "serverlist"
    override val description: String = "Displays all servers in the database."
    override val aliases: List<String> = listOf("servers")

    override fun CommandEvent.execute(args: List<String>) {
        reply(ServerCommands.listServers(), ephemeral = true)
    }
}