package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.github.GitHubClient
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("UNUSED_PARAMETER")
class ServerCommands(private val bot: DiscordBot, commands: CommandListener) {
    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", bot.config.githubToken)

    class Server(
        val keyword: String,
        val id: String,
        val name: String,
        val invite: String,
        val description: String,
        val aliases: List<String>,
    )

    class ServerJson(
        val name: String,
        val id: String,
        val size: String,
        val invite: String,
        val description: String,
        val aliases: List<String>? = null,
    )

    private var servers = setOf<Server>()
    private val disordServerPattern = "(https?://)?(www\\.)?(discord\\.gg|discord\\.com/invite)/[\\w-]+".toPattern()

    init {
        commands.add(Command("server", userCommand = true) { event, args -> event.serverCommand(args) })
        commands.add(Command("updateservers") { event, args -> event.updateServers(args) })
        commands.add(Command("serverlist") { event, args -> event.serverList(args) })
        commands.add(Command("servers") { event, args -> event.serverList(args) })

        loadServers(startup = true)
    }

    private fun loadServers(startup: Boolean) {
        val servers = try {
            val json = Utils.readStringFromClipboard() ?: "invalid json text"
            val parseStringToServers = parseStringToServers(json)
            bot.logger.info("Reading discord server list from clipboard")
            parseStringToServers
        } catch (e: JsonSyntaxException) {
            val json = github.getFileContent("data/discord_servers.json") ?: error("Error loading discord_servers")
            bot.logger.info("Reading discord server list from github")
            parseStringToServers(json)
        }

        checkForDuplicates(startup)
        Utils.runDelayed(2.seconds) {
            checkForFakes(servers)
            this.servers = servers
        }
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
            bot.logger.info("Duplicate key '$key' found in servers: ${serverList.map { it.name }}")
        }
        val count = duplicates.size
        if (count > 0) {
            bot.logger.warn("$count duplicate servers found!")
            val message = "Found $count duplicate servers:\n${duplicates.joinToString("\n")}"
            if (startup) {
                Utils.runDelayed(500.milliseconds) {
                    bot.sendMessageToBotChannel(message)
                }
            } else {
                bot.sendMessageToBotChannel(message)
            }
        } else {
            bot.logger.info("no duplicate servers found.")
        }
    }

    private fun checkForFakes(servers: MutableSet<Server>) {
        var removed = 0
        for (server in servers) {
            val removedLine = "Removed the server from the local cache!"
            Invite.resolve(bot.jda, server.invite.split("/").last(), true).queue { t ->
                val guild = t.guild ?: run {
                    bot.logger.info("Server not found in discord api '${server.name}'!")
                    bot.sendMessageToBotChannel(buildString {
                        append("Server not found in discord api '${server.name}'!\n")
                        append(removedLine)
                    })
                    servers.remove(server)
                    return@queue
                }
                if (server.id != guild.id) {
                    removed++
                    PLEADING_FACE
                    bot.logger.info("Wrong server id! ${server.name} (${server.id} != ${guild.id})")
                    bot.sendMessageToBotChannel(buildString {
                        append("$PING_HANNIBAL Wrong server id found for '${server.name}'!\n")
                        append("json id: `${server.id}`\n")
                        append("discord api id: `${guild.id}`\n")
                        append(removedLine)
                    })
                    bot.logger.info("")
                    servers.remove(server)
                }
            }
        }
        if (removed == 0) {
            bot.logger.info("Checked for fake server with no results.")
        } else {
            bot.logger.info("Removed $removed servers from local cache because of fakes or not found!")
        }

    }

    private fun MessageReceivedEvent.updateServers(args: List<String>) {
        loadServers(startup = false)
        reply("Updated server list from [GitHub](<https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/discord_servers.json>).")
        logAction("updated server list from github")
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

    private fun Server.print(tutorial: Boolean = false): String = with(this) {
        buildString {
            append("**$name**\n")
            if (description.isNotEmpty()) {
                append(description)
                append("\n")
            }
            if (!tutorial) {
                append(invite)
            } else {
                append("||In the future, you can do `!server $keyword`. Then you get this auto reply||")
            }
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

    private fun isDiscordInvite(message: String): Boolean = disordServerPattern.matcher(message).find()

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
