package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.github.GitHubClient
import at.hannibal2.skyhanni.discord.useClipboardInAge
import com.google.gson.Gson
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

object AgeFeature {
    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", BOT.config.githubTokenOwn)
    private val githubLink = "GitHub".linkTo("https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/age.json")

    data class ReleaseInfo(val name: String, val date: String)
    data class TimeSinceJson(val releases: Map<String, ReleaseInfo>)

    var releases = mapOf<String, ReleaseInfo>()
        private set
    var isLoading = false
        private set

    fun loadFromRepo() {
        isLoading = true
        Utils.runAsync("load age") {
            try {
                val json = if (useClipboardInAge) {
                    Utils.readStringFromClipboard() ?: error("error loading age json from clipboard")
                } else {
                    github.getFileContent("data/age.json") ?: error("Error loading age json from github")
                }

                val data = Gson().fromJson(json, TimeSinceJson::class.java)
                releases = data.releases
                BOT.logger.info("Loaded ${releases.size} age entries from repo")
            } finally {
                isLoading = false
            }
        }
    }

    fun format(name: String, date: String): String {
        val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        val dateTime = localDate.atStartOfDay(ZoneId.of("CET"))
        val seconds = dateTime.toEpochSecond() + 60 * 60 * 12
        return buildString {
            append("### $name was released <t:$seconds:R>.\n")
            append("### It was released on <t:$seconds:D>")
        }
    }

    @Suppress("unused")
    class AgeCommand : BaseCommand() {
        override val name = "age"
        override val description = "Show the age of something."
        override val userCommand = true

        override fun MessageReceivedEvent.execute(args: List<String>) {
            val tip = "\nTry one of those: ${releases.keys}"
            if (args.isEmpty()) {
                return reply("Usage: /age <term>$tip")
            }

            val term = args.joinToString(" ").lowercase()
            val (name, time) = releases[term] ?: run {
                return reply("Nothing found $PLEADING_FACE$tip")
            }
            reply(format(name, time))
        }
    }

    @Suppress("unused")
    class UpdateAgeListCommand : BaseCommand() {
        override val name = "updateagelist"
        override val description = "Updates the age list."
        override val aliases = listOf("ageupdate", "updateage")

        init {
            Utils.runDelayed("init load age", 1.seconds) {
                if (!isLoading) {
                    loadFromRepo()
                }
            }
        }

        override fun MessageReceivedEvent.execute(args: List<String>) {
            if (isLoading) {
                reply("Age list is already updating!")
                return
            }

            loadFromRepo()
            reply("Updated age list from $githubLink. (${releases.size} entries loaded)")
        }
    }
}