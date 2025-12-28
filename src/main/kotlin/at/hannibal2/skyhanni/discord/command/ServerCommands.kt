package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.addSeparators
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.pluralize
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyLong
import at.hannibal2.skyhanni.discord.Utils.roundTo
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.github.GitHubClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

object ServerCommands {
    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", BOT.config.githubTokenOwn)

    data class Server(
        val keyword: String,
        val id: String,
        val name: String,
        val invite: String,
        val size: Int,
        val description: String,
        val aliases: List<String>,
    ) {
        val allKeys: List<String> get() = (listOf(keyword, name) + aliases).map { it.lowercase() }

        fun print(tutorial: Boolean = false): String = buildString {
            append("**$name**\n\n")
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
        val aliases: List<String?>? = null,
    )

    var servers = setOf<Server>()
        private set
    var outage = false
        private set
    private val discordServerPattern = "(https?://)?(www\\.)?(discord\\.gg|discord\\.com/invite)/[\\w-]+".toPattern()

    var isLoading = false
        private set
    private val gson = Gson()

    // Constructor blocks until all validation completes
    private class ServerLoader {

        val servers: MutableSet<Server>
        var removed = 0
        val pendingMessages = mutableListOf<List<String>>()

        init {
            val json = github.getFileContent("data/discord_servers.json") ?: error("Error loading discord_servers data")
//        val json = Utils.readStringFromClipboard() ?: error("error loading discord_servers json from clipboard")

            servers = parseStringToServers(json)
            checkForDuplicates()
            validate()
        }

        private fun checkForDuplicates() {
            // Check for self-overlapping keys first
            servers.forEach { server ->
                val selfDuplicates = server.allKeys.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
                if (selfDuplicates.isNotEmpty()) {
                    BOT.logger.warn("Server '${server.name}' has overlapping keys: $selfDuplicates")
                    Utils.sendMessageToBotChannel("Server '${server.name}' has overlapping keys: $selfDuplicates")
                }
            }

            // Then check for duplicates between different servers
            val keyToServers = mutableMapOf<String, MutableList<Server>>()
            servers.forEach { server ->
                server.allKeys.forEach { key ->
                    keyToServers.getOrPut(key) { mutableListOf() }.add(server)
                }
            }

            val duplicates = mutableSetOf<String>()
            for ((key, serverList) in keyToServers.filter { it.value.distinct().size > 1 }) {
                duplicates.add("'$key' found in ${serverList.map { it.name }}")
                BOT.logger.info("Duplicate key '$key' found in servers: ${serverList.map { it.name }}")
            }
            val count = duplicates.size
            if (count > 0) {
                BOT.logger.warn("$count duplicate servers found!")
                val joinToString = duplicates.distinct().joinToString("\n")
                Utils.sendMessageToBotChannel("Found $count duplicate servers:\n$joinToString")
            } else {
                BOT.logger.info("No duplicate servers found.")
            }
        }

        private fun validate() {
            val results = expensiveFetchRequests()

            val memberCountDiff = mutableMapOf<String, Double>()
            val errors = mutableListOf<Throwable>()

            for ((server, result) in results) with(server) {
                result.onSuccess { validate(it, memberCountDiff) }
                result.onFailure { validateError(it, errors) }
            }
            finish(errors.size)

            memberCountDiff.memberCountFormat()
            for (error in errors) {
                throw error
            }
        }

        private fun expensiveFetchRequests(): ConcurrentHashMap<Server, Result<Invite>> {
            val results = ConcurrentHashMap<Server, Result<Invite>>(servers.size)
            val latch = CountDownLatch(servers.size)

            for (server in servers) {
                val code = server.invite.split("/").last()
                Invite.resolve(BOT.jda, code, true).queue(
                    { results[server] = Result.success(it); latch.countDown() },
                    { results[server] = Result.failure(it); latch.countDown() },
                )
            }

            latch.await()
            return results
        }

        private fun finish(errorCount: Int) {
            if (checkIfAllFailed(errorCount)) return

            this@ServerCommands.outage = false
            pendingMessages.forEach { Utils.sendMessageToBotChannel(it) }

            if (removed == 0) {
                BOT.logger.info("Checked for fake server with no results.")
            } else {
                val amount = "server".pluralize(removed, withNumber = true)
                val message = "Removed $amount from local cache because of fakes/not found/expired!"
                BOT.logger.info(message)
                Utils.sendMessageToBotChannel(message)
            }

            val link = "https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/discord_servers.json"
            val githubLink = "GitHub".linkTo(link)
            Utils.sendMessageToBotChannel("Updated server list from $githubLink.")

            this@ServerCommands.servers = servers.toSet()
        }

        private fun checkIfAllFailed(errorCount: Int): Boolean {
            val totalOriginal = servers.size + removed
            val allFailed = removed == totalOriginal && errorCount > 0

            if (!allFailed) return false
            this@ServerCommands.outage = true

            BOT.logger.warn("All servers failed validation - likely API outage. Clearing data.")
            Utils.sendMessageToBotChannel("All servers failed validation - likely API outage. Server list cleared. Please retry manually with `!serverupdate`.")

            this@ServerCommands.servers = emptySet()
            return true
        }

        private fun Server.remove() {
            removed++
            servers.remove(this)
        }

        private fun Server.validate(resolvedInvite: Invite, memberCountDiff: MutableMap<String, Double>) {
            val guild = resolvedInvite.guild ?: run {
                remove()
                BOT.logger.info("Server not found in discord api '$name'!")
                pendingMessages.add(buildList {
                    add("Server not found in discord api '$name'!")
                    add("but the invite exists? somehow? - ${resolvedInvite.url}")
                    add("Removed the server from the local cache!")
                })
                return
            }
            if (id != guild.id) {
                remove()
                BOT.logger.info("Wrong server id! $name ($id != ${guild.id})")
                pendingMessages.add(buildList {
                    add("Wrong server id found for '$name'!")
                    add("json id: `$id`")
                    add("discord api id: `${guild.id}`")
                    add("invite: (probably a scam server!?) `$invite`")
                    add("Removed the server from the local cache!")
                })
                return
            }
            memberCountDiff.calculateMemberCount(guild, this)
        }

        private fun Server.validateError(error: Throwable, errors: MutableList<Throwable>) {
            fun handle(reason: String, vararg extraLines: String) {
                remove()
                BOT.logger.info("$reason: $name ($id) = $invite")
                pendingMessages.add(
                    listOf("$reason for '$name'!") + extraLines.toList() + listOf(
                        "id: `$id`",
                        "invite: `$invite`",
                        "Removed the server from the local cache!",
                    )
                )
            }

            when (error.message) {
                "10006: Unknown Invite" -> handle("Invite not found in discord api")
                "50270: Invite is expired." -> handle("Invite expired")
                else -> {
                    handle(
                        "Error while parsing discord api",
                        "error name: ${error.javaClass.name}",
                        "error message: ${error.message}",
                    )
                    errors.add(error)
                }
            }
        }
    }

    fun loadServers() {
        isLoading = true
        Utils.runAsync("load servers") {
            try {
                ServerLoader()
            } finally {
                isLoading = false
            }
        }
    }

    private fun Map<String, Double>.memberCountFormat() {
        if (isEmpty()) {
            println("No member count update necessary")
            return
        }
        println(" ")
        for ((text, _) in entries.sortedByDescending { it.value }) {
            println(text)
        }
        println(" ")
        println("Member count update necessary: $size")
        println(" ")
    }

    private fun MutableMap<String, Double>.calculateMemberCount(guild: Invite.Guild, server: Server) {
        val accuracy = 0.01

        val realSize = guild.memberCount
        val storedSize = server.size
        val diff = realSize - storedSize
        if (diff.absoluteValue < realSize * accuracy) return

        val diffFormat = " (diff=${diff.addSeparators()})"
        val percentageChanged = ((diff.absoluteValue / realSize.toDouble()) * 100).roundTo(5)
        val name = "${formatMemberDiff(server, storedSize, realSize)} $diffFormat ${server.id}"
        val text = "$name - $percentageChanged% ($realSize)"
        this[text] = percentageChanged
    }

    private fun formatMemberDiff(
        server: Server, storedSize: Int, realSize: Int
    ) = "${server.name}: ${storedSize.addSeparators()} -> ${realSize.addSeparators()}"

    private fun parseStringToServers(json: String): MutableSet<Server> {
        val type = object : TypeToken<Map<String, Map<String, ServerJson>>>() {}.type
        val data: Map<String, Map<String, ServerJson>> = gson.fromJson(json, type)

        return data.flatMap { (_, serverCategories) ->
            serverCategories.map { (id, data) ->
                Server(
                    id.lowercase(),
                    data.id,
                    data.name,
                    data.invite,
                    data.size.toInt(),
                    data.description,
                    data.aliases?.map { it?.lowercase() ?: error("aliases contains null for server id ${data.id}") }
                        ?: emptyList())
            }
        }.toMutableSet()
    }

    internal fun getServer(name: String): Server? {
        val lowercaseName = name.lowercase()
        return servers.find { server ->
            server.keyword.equals(lowercaseName, ignoreCase = true) ||
                    server.name.equals(lowercaseName, ignoreCase = true) ||
                    lowercaseName in server.aliases
        }
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
        if (args.size !in 1..2) return wrongUsage("<keyword>")
        val keyword = args.first()
        val debug = args.getOrNull(1) == "-d"
        val server = ServerCommands.getServer(keyword)
        if (server == null) {
            if (ServerCommands.outage) {
                userError("Server list unavailable due to API outage. Please try again later.")
            } else {
                userError("Server with keyword '$keyword' not found.")
            }
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
    override val aliases: List<String> = listOf(
        "updateservers", "updateserverlist", "serverlistupdate", "listupdateserver", "updateserver"
    )

    init {
        Utils.runDelayed("init load servers", 1.seconds) {
            ServerCommands.loadServers()
        }
    }

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (ServerCommands.isLoading) {
            reply("Server list is already updating!")
            return
        }

        logAction("Started server list update")
        reply("Updating server list ...")
        ServerCommands.loadServers()
    }
}

@Suppress("unused")
class ServerList : BaseCommand() {
    override val name: String = "serverlist"
    override val description: String = "Displays all servers in the database."
    override val aliases: List<String> = listOf("servers")

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (ServerCommands.servers.isEmpty()) {
            if (ServerCommands.outage) {
                reply("Server list unavailable due to API outage.")
            } else {
                reply("No servers found.")
            }
            return
        }
        val list = ServerCommands.servers.joinToString("\n") { server ->
            val aliases = server.aliases
            if (aliases.isNotEmpty()) "${server.keyword} [${aliases.joinToString(", ")}]"
            else server.keyword
        }
        replyLong("Server list:\n$list")
    }
}