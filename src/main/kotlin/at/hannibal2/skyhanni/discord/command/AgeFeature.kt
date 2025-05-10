package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.pluralize
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.github.GitHubClient
import com.google.gson.Gson
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object AgeFeature {
    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", BOT.config.githubTokenOwn)

    data class ReleaseInfo(val name: String, val date: String)

    data class TimeSinceJson(val releases: Map<String, ReleaseInfo>)

    var releases = mapOf<String, ReleaseInfo>()

    private fun loadFromRepo() {
        val json = github.getFileContent("data/age.json") ?: error("Error loading age json data")
//        val json = Utils.readStringFromClipboard()

        val gson = Gson()
        val data = gson.fromJson(json, TimeSinceJson::class.java)
        releases = data.releases
    }

    @Suppress("unused")
    class AgeCommand : BaseCommand() {
        override val name: String = "age"
        override val description: String = "Show the age of something."

        override fun MessageReceivedEvent.execute(args: List<String>) {
            if (releases.isEmpty()) {
                loadFromRepo()
            }

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

    @Suppress("SameParameterValue")
    fun format(name: String, date: String): String {
        val releaseDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)

        val zone = ZoneId.of("CET")
        val releaseZdt = releaseDate.atStartOfDay(zone)
        val now = ZonedDateTime.now(zone)
        val period = Period.between(releaseZdt.toLocalDate(), now.toLocalDate())

        val parts = mutableListOf<String>().apply {
            if (period.years != 0) add("year".pluralize(period.years, withNumber = true))
            if (period.months != 0) add("month".pluralize(period.months, withNumber = true))
            if (period.days != 0 || isEmpty()) add("day".pluralize(period.days, withNumber = true))
        }

        val age = parts.joinToString(" ")
        val releasedOn = releaseDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))

        return "### $name is $age old.\n### It was released on $releasedOn"
    }

    @Suppress("unused")
    class UpdateAgeListCommand : BaseCommand() {
        override val name: String = "updateagelist"
        override val description: String = "Updates the age list."
        override val aliases: List<String> = listOf("ageupdate", "updateage")

        override fun MessageReceivedEvent.execute(args: List<String>) {
            reply("updating age list ...")
            loadFromRepo()
            val link = "GitHub".linkTo("https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/age.json")
            reply("Updated age list from $link.")
        }
    }
}