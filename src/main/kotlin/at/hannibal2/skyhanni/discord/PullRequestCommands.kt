package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.SimpleTimeMark.Companion.asTimeMark
import at.hannibal2.skyhanni.discord.Utils.createParentDirIfNotExist
import at.hannibal2.skyhanni.discord.Utils.embed
import at.hannibal2.skyhanni.discord.Utils.format
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.timeExecution
import at.hannibal2.skyhanni.discord.Utils.uploadFile
import at.hannibal2.skyhanni.discord.github.GitHubClient
import at.hannibal2.skyhanni.discord.json.discord.PullRequestJson
import at.hannibal2.skyhanni.discord.json.discord.Status
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.awt.Color
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

@Suppress("ReturnCount")
class PullRequestCommands(config: BotConfig, commands: Commands) {

    private val github = GitHubClient("hannibal002", "SkyHanni", config.githubToken)

    init {
        commands.add(Command("pr") { event, args -> event.pullRequestCommand(args) })
    }

    private val runIdRegex = Regex("https://github\\.com/[\\w.]+/[\\w.]+/actions/runs/(?<RunId>\\d+)/job/(?<JobId>\\d+)")

    private fun MessageReceivedEvent.pullRequestCommand(args: List<String>) {
        if (args.size != 2) {
            reply("Usage: `!pr <number>`")
            return
        }
        val prNumber = args[1].toIntOrNull() ?: run {
            reply("unknown number \uD83E\uDD7A (${args[1]})")
            return
        }
        logAction("loads pr infos for #$prNumber")

        val prLink = "https://github.com/hannibal002/SkyHanni/pull/$prNumber"

        val pr = try {
            github.findPullRequest(prNumber) ?: run {
                reply("pr is null!")
                return
            }
        } catch (e: IllegalStateException) {
            if (e.message == "GitHub API error: 404") {
                val issueUrl = "https://github.com/hannibal002/SkyHanni/issues/$prNumber"
                val issue = "issue".linkTo(issueUrl)
                val text = "This pull request does not yet exist or is an $issue"
                reply(embed("Not found \uD83E\uDD7A", text, Color.red))
                return
            }
            reply("error while finding pull request: ${e.message}")
            return
        }

        val head = pr.head
        val userName = head.user.login
        val userProfile = "https://github.com/$userName"
        val prNumberDisplay = "#$prNumber".linkTo(prLink)
        val userNameDisplay = userName.linkTo(userProfile)
        val embedTitle = pr.title
        val title = buildString {
            append("> $prNumberDisplay by $userNameDisplay")
            append("\n")
        }

        val time = buildString {
            val lastUpdate = passedSince(pr.updatedAt)
            val created = passedSince(pr.createdAt)
            append("> Created: $created")
            append("\n")
            append("> Last Updated: $lastUpdate")
            append("\n")
        }

        val lastCommit = head.sha

        val job = github.getRun(lastCommit, "Build and test") ?: run {
            val text = "${title}${time} \nUnable to locate run \uD83E\uDD7A (expired or does not exist)"
            reply(embed(embedTitle, text, readColor(pr)))
            return
        }

        if (job.status != Status.COMPLETED) {
            val text = when (job.status) {
                Status.REQUESTED -> "Run has been requested \uD83E\uDD7A"
                Status.QUEUED -> "Run is in queue \uD83E\uDD7A"
                Status.IN_PROGRESS -> "Run is in progress \uD83E\uDD7A"
                Status.WAITING -> "Run is waiting \uD83E\uDD7A"
                Status.PENDING -> "Run is pending \uD83E\uDD7A"
                else -> ""
            }
            reply(embed(embedTitle, "${title}${time} \n $text", readColor(pr)))
            return
        }

        val match = job.htmlUrl?.let { runIdRegex.matchEntire(it) }
        val runId = match?.groups?.get("RunId")?.value

        val artifactLink = "https://github.com/hannibal002/SkyHanni/actions/runs/$runId?pr=$prNumber"
        val nightlyLink = "https://nightly.link/hannibal002/SkyHanni/actions/runs/$runId/Development%20Build.zip"
        val artifactLine = "GitHub".linkTo(artifactLink)
        val nightlyLine = "Nightly".linkTo(nightlyLink)

        val artifactDisplay = buildString {
            append(" \n")
            append("Download the latest development build of this pr!")
            append("\n")
            append("> From $artifactLine (requires an GitHub Account)")
            append("\n")
            append("> From $nightlyLine (unofficial)")
            append("\n")
            append("> (updated ${passedSince(job.completedAt ?: "")})")
        }

        reply(embed(embedTitle, "$title$time$artifactDisplay", readColor(pr)))
//        reply("$title$time$artifactDisplay")
    }

    // colors picked from github
    private fun readColor(pr: PullRequestJson): Color = when {
        pr.draft -> Color(101, 108, 118)
        pr.merged -> Color(130, 86, 208)
        else -> Color(52, 125, 57)
    }

    private fun parseToUnixTime(isoTimestamp: String): Long =
        Instant.from(DateTimeFormatter.ISO_INSTANT.parse(isoTimestamp)).epochSecond

    private fun toTimeMark(stringTime: String): SimpleTimeMark = (parseToUnixTime(stringTime) * 1000).asTimeMark()

    private fun passedSince(stringTime: String): String = "<t:${parseToUnixTime(stringTime)}:R>"

    @Suppress("unused") // TODO implement once we can upload the file
    private fun MessageReceivedEvent.pullRequestArtifactCommand(args: List<String>) {
        if (args.size != 2) {
            reply("Usage: `!prupload <number>`")
            return
        }
        val prNumber = args[1].toIntOrNull() ?: run {
            reply("unknwon number \uD83E\uDD7A (${args[1]})")
            return
        }

        val prLink = "https://github.com/hannibal002/SkyHanni/pull/$prNumber"
        reply("Looking for pr <$prLink..")

        val pr = github.findPullRequest(prNumber) ?: run {
            reply("pr is null!")
            return
        }
        val head = pr.head
        reply("found pr `${pr.title}` (from `${head.user.login}`)")

        reply("looking for artifact ..")
        val lastCommit = head.sha
        val artifact = github.findArtifact(lastCommit) ?: run {
            reply("artifact is null!")
            return
        }
        reply("found artifact")

        val artifactId = artifact.id
        val fileRaw = File("temp/downloads/artifact-$artifactId-raw")
        fileRaw.createParentDirIfNotExist()
        val fileUnzipped = File("temp/downloads/artifact-$artifactId-unzipped")
        reply("Downloading artifact ..")
        val (_, downloadTime) = timeExecution {
            github.downloadArtifact(artifactId, fileRaw)
        }
        reply("artifact downnloaded in ${downloadTime.format()}")

        Utils.unzipFile(fileRaw, fileUnzipped)
        fileRaw.delete()

        val displayUrl = "https://github.com/hannibal002/SkyHanni/actions/runs/$artifactId?pr=$prNumber"

        val modJar = findJarFile(fileUnzipped) ?: run {
            reply("mod jar not found!")
            return
        }

        reply("start uploading..")
        val (_, uploadTime) = timeExecution {
            channel.uploadFile(modJar, "here is the jar from <$displayUrl>")
        }
        reply("Mod jar uploaded in ${uploadTime.format()}")
    }

    private fun findJarFile(directory: File): File? {
        return directory.walkTopDown().firstOrNull { it.isFile && it.name.startsWith("SkyHanni-") }
    }
}
