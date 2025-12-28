package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.addSeparators
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.pluralize
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.roundTo
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.github.GitHubClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
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
        val aliases: List<String?>? = null,
    )

    var servers = setOf<Server>()
        private set
    private val discordServerPattern = "(https?://)?(www\\.)?(discord\\.gg|discord\\.com/invite)/[\\w-]+".toPattern()

    var serverLoader: ServerLoader? = null

    class ServerLoader(val onFinish: (Int) -> Unit = { _ -> }) {

        val servers: MutableSet<Server>
        var removed: AtomicInteger
        var latch: CountDownLatch

        init {
            val json = github.getFileContent("data/discord_servers.json") ?: error("Error loading discord_servers data")
//        val json = Utils.readStringFromClipboard() ?: error("error loading discord_servers json from clipboard")

            servers = parseStringToServers(json)
            checkForDuplicates()
            latch = CountDownLatch(servers.size)
            removed = AtomicInteger(0)
            validate()
        }

        private fun checkForDuplicates() {
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
                Utils.sendMessageToBotChannel(message)
            } else {
                BOT.logger.info("no duplicate servers found.")
            }
        }

        private fun validate() {
            val memberCountDiff = mutableMapOf<String, Double>()
            // we need to throw the errors outside of Invite.resolve, sadly
            val errors = mutableListOf<Throwable>()

            for (server in servers.toList()) with(server) {
                Invite.resolve(BOT.jda, invite.split("/").last(), true).queue(
                    { validate(it, memberCountDiff) },
                    { error -> validateError(error, errors) },
                )
            }

            latch.await() // wait for all servers to be checked
            finish()

            memberCountDiff.memberCountFormat()
            for (error in errors) {
                throw error
            }
        }

        fun finish() {
            val removed = removed.get()
            if (removed == 0) {
                BOT.logger.info("Checked for fake server with no results.")
            } else {
                val amount = "server".pluralize(removed, withNumber = true)
                val message = "Removed $amount from local cache because of fakes/not found/expired!"
                BOT.logger.info(message)
                Utils.sendMessageToBotChannel(message)
            }
            onFinish(removed)
            this@ServerCommands.servers = servers
        }

        fun remove(server: Server) {
            removed.incrementAndGet()
            servers.remove(server)
            latch.countDown()
        }

        private fun Server.validate(invite: Invite, memberCountDiff: MutableMap<String, Double>) {
            val guild = invite.guild ?: run {
                remove(this)
                BOT.logger.info("Server not found in discord api '$name'!")
                Utils.sendMessageToBotChannel(buildList {
                    add("Server not found in discord api '$name'!")
                    add("but the invite exists? somehow? - $invite")
                    add("Removed the server from the local cache!")
                })
                return
            }
            if (id != guild.id) {
                // TODO make this server var here inline
                val server = this
                remove(this)
                BOT.logger.info("Wrong server id! $name ($id != ${guild.id})")
                Utils.sendMessageToBotChannel(buildList {
                    add("Wrong server id found for '$name'!")
                    add("json id: `id`")
                    add("discord api id: `${guild.id}`")
                    add("invite: (probably a scam server!?)" + "link".linkTo(server.invite))
                    add("Removed the server from the local cache!")
                })
                return
            }
            memberCountDiff.calcualteMemberCount(guild, this)
            latch.countDown()
        }

        private fun Server.validateError(error: Throwable, errors: MutableList<Throwable>) {
            if (error.message == "10006: Unknown Invite") {
                remove(this)
                BOT.logger.info("Unknown server invite: $name ($id) = $invite")
                Utils.sendMessageToBotChannel(buildList {
                    add("Invite not found in discord api for '$name'!")
                    add("json id: `$id`")
                    add("Old invite: <$invite>")
                    add("Removed the server from the local cache!")
                })
            } else if (error.message == "50270: Invite is expired.") {
                remove(this)
                BOT.logger.info("Expired server invite: $name ($id) = $invite")
                Utils.sendMessageToBotChannel(buildList {
                    add("Invite expired for '$name'!")
                    add("json id: `$id`")
                    add("Expired invite: <$invite>")
                    add("Removed the server from the local cache!")
                })
            } else {
                remove(this)
                BOT.logger.info("Error with server invite: $name ($id) = $invite")
                Utils.sendMessageToBotChannel(buildList {
                    add("Error while parsing discord api for '$name'!")
                    add("error name: ${error.javaClass.name}")
                    add("error message: ${error.message}")
                    add("json id: `$id`")
                    add("Old invite: <$invite>")
                    add("Removed the server from the local cache!")
                })
                errors.add(error)
            }
        }
    }

    fun loadServers(onFinish: (Int) -> Unit = { _ -> }) {
        serverLoader = ServerLoader(onFinish)
    }

    private fun Map<String, Double>.memberCountFormat() {
        if (!isNotEmpty()) {
            println("no member count update necessary")
            return
        }
        println(" ")
        for ((text, diff) in entries.sortedBy { it.value }.reversed()) {
            println(text)
        }
        println(" ")
        println("member count update necessary: $size")
        println(" ")
    }

    private fun MutableMap<String, Double>.calcualteMemberCount(guild: Invite.Guild, server: Server) {
        val accuracy = 0.01

        val realSize = guild.memberCount
        val storedSize = server.size
        val diff = realSize - storedSize
        if (diff.absoluteValue < realSize * accuracy) return

        val diffFormat = " (diff=${diff.addSeparators()})"
        val percentageChanged = ((diff.absoluteValue / realSize.toDouble()) * 100).roundTo(5)
        val name = "${s(server, storedSize, realSize)} $diffFormat ${server.id}"
        val text = "$name - $percentageChanged% ($realSize)"
        this[text] = percentageChanged
    }

    private fun s(
        server: Server, storedSize: Int, realSize: Int
    ) = "${server.name}: ${storedSize.addSeparators()} -> ${realSize.addSeparators()}"

    private fun parseStringToServers(json: String): MutableSet<Server> {
        val type = object : TypeToken<Map<String, Map<String, ServerJson>>>() {}.type
        val data: Map<String, Map<String, ServerJson>> = Gson().fromJson(json, type)

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
        if (args.size !in 1..2) return wrongUsage("<keyword>")
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
    override val aliases: List<String> = listOf(
        "updateservers", "updateserverlist", "serverlistupdate", "listupdateserver", "updateserver"
    )

    init {
        Utils.runDelayed("init load servers", 1.seconds) {
            ServerCommands.loadServers()
        }
    }

    override fun MessageReceivedEvent.execute(args: List<String>) {
        reply("updating server list ...")
        if (ServerCommands.serverLoader != null) {
            reply("Server list is already updating!")
            return
        }
        ServerCommands.loadServers { removed ->
            val removedSuffix = if (removed > 0) {
                " (removed $removed servers)"
            } else ""
            val source =
                "GitHub".linkTo("https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/discord_servers.json")
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