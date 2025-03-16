package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.sendMessageToBotChannel
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.command.ServerCommands.loadServers
import at.hannibal2.skyhanni.discord.github.GitHubClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.milliseconds

object ServerCommands {
    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", BOT.config.githubToken)

    data class Server(
        val keyword: String,
        val id: String,
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
        val id: String,
        val size: String,
        val invite: String,
        val description: String,
        val aliases: List<String>? = null,
    )

    var servers = setOf<Server>()
        private set
    private val discordServerPattern = "(https?://)?(www\\.)?(discord\\.gg|discord\\.com/invite)/[\\w-]+".toPattern()

    fun loadServers(startup: Boolean, onFinish: (String, Int) -> Unit = { _, _ -> }) {
        var source: String
        val servers = try {
            val json = Utils.readStringFromClipboard() ?: "invalid json text"
            val list = parseStringToServers(json)
            BOT.logger.info("Reading discord server list from clipboard")
            source = "local clipboard"
            list
        } catch (e: Throwable) { // JsonSyntaxException or NullPointerException
            val json = github.getFileContent("data/discord_servers.json") ?: error("Error loading discord_servers")
            BOT.logger.info("Reading discord server list from github")
            source = "[GitHub](<https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/discord_servers.json>)"
            parseStringToServers(json)
        }

        Utils.runDelayed(if (startup) 500.milliseconds else 2.milliseconds) { // We need a delay on startup only
            checkForDuplicates(servers, startup)
            checkForFakes(servers) { removed ->
                if (removed == 0) {
                    BOT.logger.info("Checked for fake server with no results.")
                } else {
                    BOT.logger.info("Removed $removed servers from local cache because of fakes or not found!")
                }
                onFinish(source, removed)
                this.servers = servers
            }
        }
    }

    private fun checkForFakes(servers: MutableSet<Server>, onFinish: (Int) -> Unit) {
        var removed = 0
        val latch = CountDownLatch(servers.size)

        for (server in servers.toList()) {
            Invite.resolve(BOT.jda, server.invite.split("/").last(), true).queue { t ->
                val guild = t.guild ?: run {
                    BOT.logger.info("Server not found in discord api '${server.name}'!")
                    sendMessageToBotChannel(buildString {
                        append("Server not found in discord api '${server.name}'!\n")
                        append("Removed the server from the local cache!")
                    })
                    servers.remove(server)
                    latch.countDown()
                    return@queue
                }
                if (server.id != guild.id) {
                    removed++
                    BOT.logger.info("Wrong server id! ${server.name} (${server.id} != ${guild.id})")
                    sendMessageToBotChannel(buildString {
                        append("Wrong server id found for '${server.name}'!\n")
                        append("json id: `${server.id}`\n")
                        append("discord api id: `${guild.id}`\n")
                        append("Removed the server from the local cache!")
                    })
                    servers.remove(server)
                }
                latch.countDown()
            }
        }

        latch.await() // wait for all servers to be checked
        onFinish(removed)
    }

    private fun parseStringToServers(json: String): MutableSet<Server> {
        val type = object : TypeToken<Map<String, Map<String, ServerJson>>>() {}.type
        val data: Map<String, Map<String, ServerJson>> = Gson().fromJson(json, type)

        return data.flatMap { (_, serverCategories) ->
            serverCategories.map { (id, data) ->
                Server(id.lowercase(),
                    data.id,
                    data.name,
                    data.invite,
                    data.description,
                    data.aliases?.map { it.lowercase() } ?: emptyList())
            }
        }.toMutableSet()
    }

    private fun checkForDuplicates(servers: MutableSet<Server>, startup: Boolean) {
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
                    continue // skip if the server name is the same as the key name
                }
            }
            duplicates.add("'$key' found in ${serverList.map { it.name }}")
            BOT.logger.info("Duplicate key '$key' found in servers: ${serverList.map { it.name }}")
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

    fun isKnownServerUrl(event: MessageReceivedEvent, message: String): Boolean {
        val server = getServerByInviteUrl(message) ?: run {
            if (isDiscordInvite(message)) {
                event.logAction("sends unknown discord invite '$message'")
            }
            return false
        }

        event.reply(server.print(tutorial = true))
        return true
    }
}

@Suppress("unused")
class ServerCommand : BaseCommand() {
    override val name: String = "server"
    override val description: String = "Displays information about a server from our 'useful server list'."
    override val options: List<Option> = listOf(
        Option("keyword", "Keyword of the server you want to display."),
        Option("debug", "Display even more useful information (-d to use).", required = false)
    )
    override val userCommand: Boolean = true

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (args.size !in 1..2) {
            wrongUsage("<keyword>")
            return
        }
        val keyword = args.first()
        val debug = args.getOrNull(1) == "-d"
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

    init {
        loadServers(startup = true)
    }

    override fun MessageReceivedEvent.execute(args: List<String>) {
        reply("updating server list ...")
        loadServers(startup = false) { source, removed ->
            val removedSuffix = if (removed > 0) {
                " (removed $removed servers)"
            } else ""
            reply("Updated server list from $source.$removedSuffix")
            logAction("updated server list from github")
        }
    }

}

@Suppress("unused")
class ServerList : BaseCommand() {
    override val name: String = "serverlist"
    override val description: String = "Displays all servers in the database."
    override val aliases: List<String> = listOf("servers")

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (ServerCommands.servers.isEmpty()) {
            reply("No servers found.")
            return
        }
        val list = ServerCommands.servers.joinToString("\n") { server ->
            val aliases = server.aliases
            if (aliases.isNotEmpty()) "${server.keyword} [${aliases.joinToString(", ")}]"
            else server.keyword
        }
        reply("Server list:\n$list")
    }
}