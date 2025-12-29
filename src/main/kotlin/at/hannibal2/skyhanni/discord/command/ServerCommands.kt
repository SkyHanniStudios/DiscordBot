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
import at.hannibal2.skyhanni.discord.useClipboardInServerList
import at.hannibal2.skyhanni.discord.utils.LiveLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

object ServerCommands {

    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", BOT.config.githubTokenOwn)
    private val gson = Gson()

    var servers = setOf<Server>()
        private set
    var outage = false
        private set
    var isLoading = false
        private set

    private val discordServerPattern = "(https?://)?(www\\.)?(discord\\.gg|discord\\.com/invite)/[\\w-]+".toPattern()

    data class Server(
        val keyword: String,
        val id: String,
        val name: String,
        val invite: String,
        val size: Int,
        val description: String,
        val aliases: List<String>,
    ) {
        val allKeys: Set<String> get() = (listOf(keyword, name) + aliases).map { it.lowercase() }.toSet()

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
                        ?: emptyList()
                )
            }
        }.toMutableSet()
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

    private fun formatMemberDiff(server: Server, storedSize: Int, realSize: Int) =
        "${server.name}: ${storedSize.addSeparators()} -> ${realSize.addSeparators()}"

    private class ServerLoader {
        val servers: MutableSet<Server>
        var removedInvalidInvite = 0
        var removedWrongId = 0
        var removedOther = 0
        val removed get() = removedInvalidInvite + removedWrongId + removedOther

        val pendingMessages = mutableListOf<List<String>>()
        val githubLink =
            "GitHub".linkTo("https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/discord_servers.json")

        val viaText = if (useClipboardInServerList) "dev clipboard" else githubLink
        val log = LiveLog(Utils.getBotChannel(), "Server List Update (via $viaText)")

        init {
            log.startAutoUpdate()
            try {
                log.status("Loading from GitHub...")
                val json = if (useClipboardInServerList) {
                    Utils.readStringFromClipboard() ?: error("error loading discord_servers json from clipboard")
                } else {
                    github.getFileContent("data/discord_servers.json") ?: error("Error loading discord_servers data")
                }

                log.log("Parsing JSON...")
                servers = parseStringToServers(json)
                log.log("Found ${servers.size} servers")

                log.status("Checking duplicates...")
                checkForDuplicates()

                log.status("Validating invites...")
                validate()
            } catch (e: Exception) {
                log.complete("Error: ${e.message}", status = "Failed")
                throw e
            }
        }

        private fun checkForDuplicates() {
            // Check for self-overlapping keys first
            servers.forEach { server ->
                val keywordLower = server.keyword.lowercase()
                val nameLower = server.name.lowercase()

                val problemAliases = server.aliases
                    .map { it.lowercase() }
                    .filter { alias ->
                        alias == keywordLower ||
                                alias == nameLower ||
                                server.aliases.count { it.lowercase() == alias } > 1
                    }
                    .distinct()

                if (problemAliases.isNotEmpty()) {
                    BOT.logger.warn("Server '${server.name}' has overlapping aliases: $problemAliases")
                    Utils.sendMessageToBotChannel("Server '${server.name}' has overlapping aliases: $problemAliases")
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

            finish(errors)
            memberCountDiff.memberCountFormat()
        }

        private fun expensiveFetchRequests(): ConcurrentHashMap<Server, Result<Invite>> {
            val results = ConcurrentHashMap<Server, Result<Invite>>(servers.size)
            val latch = CountDownLatch(servers.size)
            val completed = AtomicInteger(0)
            val total = servers.size

            for (server in servers) {
                val code = server.invite.split("/").last()
                Invite.resolve(BOT.jda, code, true).queue(
                    {
                        results[server] = Result.success(it)
                        log.progress(completed.incrementAndGet(), total)
                        latch.countDown()
                    },
                    {
                        results[server] = Result.failure(it)
                        log.progress(completed.incrementAndGet(), total)
                        latch.countDown()
                    },
                )
            }

            latch.await()
            return results
        }

        private fun finish(errors: List<Throwable>) {
            if (checkIfAllFailed()) {
                // Only show first 3 messages during outage for context
                val onlyFirst = 3
                pendingMessages.take(onlyFirst).forEach { Utils.sendMessageToBotChannel(it) }
                if (pendingMessages.size > onlyFirst) {
                    Utils.sendMessageToBotChannel("... and ${pendingMessages.size - onlyFirst} more errors (outage detected)")
                }
                return
            }

            pendingMessages.forEach { Utils.sendMessageToBotChannel(it) }

            this@ServerCommands.outage = false

            if (removed == 0) {
                BOT.logger.info("Checked for fake server with no results.")
            } else {
                val amount = "server".pluralize(removed, withNumber = true)
                BOT.logger.info("Removed $amount from local cache because of fakes/not found/expired!")
            }

            val found = servers.size + removed
            val active = servers.size

            val breakdown = buildList {
                if (removedInvalidInvite > 0) add("$removedInvalidInvite bad invite")
                if (removedWrongId > 0) add("$removedWrongId wrong ID")
                if (removedOther > 0) add("$removedOther other")
            }.joinToString(", ")

            val removedInfo = if (removed > 0) ", $removed removed ($breakdown)" else ""

            val errorInfo = if (errors.isNotEmpty()) {
                errors.forEach { BOT.logger.error("Unexpected validation error", it) }
                ", ${errors.size} unexpected errors"
            } else ""

            val status = if (errors.isNotEmpty()) "Done (with errors)" else "Done"
            log.complete("$found found, $active active$removedInfo$errorInfo", status = status)

            this@ServerCommands.servers = servers.toSet()
        }

        private fun checkIfAllFailed(): Boolean {
            val allFailed = servers.isEmpty() && removed > 0

            if (!allFailed) return false
            this@ServerCommands.outage = true

            BOT.logger.warn("All servers failed validation. Clearing data.")
            log.complete("All servers failed. Retry with `!serverupdate`.", status = "Failed")

            this@ServerCommands.servers = emptySet()
            return true
        }

        private fun Server.validate(resolvedInvite: Invite, memberCountDiff: MutableMap<String, Double>) {
            val guild = resolvedInvite.guild ?: run {
                removedOther++
                log.issue("Server not found in discord api '$name'!")
                servers.remove(this)
                BOT.logger.info("Server not found in discord api '$name'!")
                pendingMessages.add(buildList {
                    add("Server not found in discord api '$name'!")
                    add("but the invite exists? somehow? - ${resolvedInvite.url}")
                    add("Removed the server from the local cache!")
                })
                return
            }
            if (id != guild.id) {
                removedWrongId++
                log.issue("Wrong server id for '$name'! (scam?)")
                servers.remove(this)
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
                log.issue("$reason for '$name'!")
                servers.remove(this)
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
                "10006: Unknown Invite" -> {
                    removedInvalidInvite++
                    handle("Invite not found in discord api")
                }

                "50270: Invite is expired." -> {
                    removedInvalidInvite++
                    handle("Invite expired")
                }

                else -> {
                    removedOther++
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
                userError("Server list currently unavailable. Please try again later.")
            } else if (ServerCommands.servers.isEmpty()) {
                reply("Server list not loaded yet, please try again in a moment.")
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
            if (!ServerCommands.isLoading) {
                ServerCommands.loadServers()
            }
        }
    }

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (ServerCommands.isLoading) {
            reply("Server list is already updating!")
            return
        }

        logAction("Started server list update")
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
                reply("Server list currently unavailable.")
            } else {
                reply("Server list not loaded yet, please try again in a moment.")
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