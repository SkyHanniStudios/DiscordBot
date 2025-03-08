package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.reply
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.io.File

@Suppress("UNUSED_PARAMETER")
class ServerCommands(private val config: BotConfig, commands: CommandListener) {

    class Server(
        val keyword: String,
        val name: String,
        val invite: String,
        val description: String,
        val aliases: List<String>,
    )

    class ServerJson(
        val name: String,
        val size: String,
        val invite: String,
        val description: String,
        val aliases: List<String>? = null,
    )

    private var servers = listOf<Server>()

    init {
        commands.add(Command("server", userCommand = true) { event, args -> event.serverCommand(args) })
        commands.add(Command("updateservers") { event, args -> event.updateServers(args) })
        commands.add(Command("serverlist") { event, args -> event.serverList(args) })
        commands.add(Command("servers") { event, args -> event.serverList(args) })

        loadServers()
    }

    private fun loadServers() {
        val json = try {
            File("data/discord_servers.json").readText()
        } catch (ex: Exception) {
            error("Could not load server data config.")
        }

        // Parse JSON as a map of maps.
        val type = object : TypeToken<Map<String, Map<String, ServerJson>>>() {}.type
        val data: Map<String, Map<String, ServerJson>> = Gson().fromJson(json, type)

        // Flatten into a list of Mods (ignoring category)
        servers = data.flatMap { (_, serverCategories) ->
            serverCategories.map { (id, data) ->
                Server(
                    id.lowercase(),
                    data.name,
                    data.invite,
                    data.description,
                    data.aliases?.map { it.lowercase() } ?: emptyList())
            }
        }
    }

    private fun MessageReceivedEvent.updateServers(args: List<String>) {
        loadServers()
        reply("updated server list!")
    }

    private fun MessageReceivedEvent.serverCommand(args: List<String>) {
        if (args.size !in 2..3) {
            reply("Usage: !server <keyword>")
            return
        }
        val keyword = args[1]
        val debug = args.getOrNull(2) == "-d"
        val server = getServer(keyword.lowercase())
        if (server != null) {
            if (debug) {
                reply(server.printDebug())
            } else {
                reply(server.print())
            }
        } else {
            reply("Server with keyword '$keyword' not found.")
        }
    }

    private fun getServer(name: String): Server? {
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

    private fun Server.print(): String = with(this) {
        buildString {
            append("**$name**\n")
            if (description.isNotEmpty()) {
                append(description)
                append("\n")
            }
            append(invite)
        }
    }

    private fun Server.printDebug(): String = with(this) {
        buildString {
            append("keyword: '$keyword'\n")
            append("displayName: '$name'\n")
            append("description: '$description'\n")
            append("inviteLink: '<$invite>'\n")
            append("aliases: $aliases\n")
        }
    }

    private fun MessageReceivedEvent.serverList(args: List<String>) {
        if (servers.isEmpty()) {
            reply("No servers found.")
            return
        }
        val list = servers.joinToString("\n") { server ->
            val aliases = server.aliases
            if (aliases.isNotEmpty()) "${server.keyword} [${aliases.joinToString(", ")}]"
            else server.keyword
        }
        reply("Server list:\n$list")
    }
}
