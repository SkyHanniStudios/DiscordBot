package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.SimpleTimeMark
import at.hannibal2.skyhanni.discord.SimpleTimeMark.Companion.asTimeMark
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.createParentDirIfNotExist
import at.hannibal2.skyhanni.discord.Utils.embed
import at.hannibal2.skyhanni.discord.Utils.format
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.runDelayed
import at.hannibal2.skyhanni.discord.Utils.timeExecution
import at.hannibal2.skyhanni.discord.Utils.uploadFile
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.github.GitHubClient
import at.hannibal2.skyhanni.discord.json.discord.PullRequestJson
import at.hannibal2.skyhanni.discord.json.discord.Status
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.awt.Color
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

object PullRequestCommand : BaseCommand() {

    override val name: String = "pr"

    override val description: String = "Displays useful information about a pull request on Github."
    override val options: List<Option> = listOf(
        Option("number", "Number of the pull request you want to display.")
    )

    override val userCommand: Boolean = true

    private const val USER = "hannibal002"
    private const val REPO = "SkyHanni"
    private val github = GitHubClient(USER, REPO, BOT.config.githubToken)
    private const val BASE = "https://github.com/$USER/$REPO"

    private val runIdRegex =
        Regex("https://github\\.com/[\\w.]+/[\\w.]+/actions/runs/(?<RunId>\\d+)/job/(?<JobId>\\d+)")
    private val pullRequestPattern = "$BASE/pull/(?<pr>\\d+)".toPattern()

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (args.size != 1) return wrongUsage("<number>")
        val first = args.first()
        val prNumber = first.toIntOrNull() ?: run {
            userError("Unknown number $PLEADING_FACE ($first)")
            return
        }
        if (prNumber < 1) {
            userError("PR number needs to be positive $PLEADING_FACE")
            return
        }
        loadPrInfos(prNumber)
    }

    private fun MessageReceivedEvent.loadPrInfos(prNumber: Int) {
        logAction("loads pr infos for #$prNumber")

        val prLink = "$BASE/pull/$prNumber"

        val pr = try {
            github.findPullRequest(prNumber) ?: run {
                reply("pr is null!")
                return
            }
        } catch (e: IllegalStateException) {
            if (e.message?.contains(" code:404 ") == true) {
                val issueUrl = "$BASE/issues/$prNumber"
                val issue = "issue".linkTo(issueUrl)
                val text = "This pull request does not yet exist or is an $issue"
                reply(embed("Not found $PLEADING_FACE", text, Color.red))
                return
            }
            reply("Could not load pull request infos for #$prNumber: ${e.message}")
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
            if (pr.merged) {
                val merged = passedSince(pr.mergedAt ?: "")
                append("> Merged: $merged")
                append("\n")

                val releases = try {
                    github.getReleases()
                } catch (e: Exception) {
                    null
                }
                val lastRelease = releases?.firstOrNull()
                val lastReleaseTime = passedSince(lastRelease?.publishedAt ?: "")
                append("> Last Release: $lastReleaseTime")
                append("\n")

                if (releaseSinceMerge(pr.mergedAt ?: "", lastRelease?.publishedAt ?: "")) {
                    append("> This PR is in the latest beta.")
                    append("\n")
                } else {
                    append("> This PR is not in the latest beta.")
                    append("\n")
                }
            }
        }

        val lastCommit = head.sha

        val job = github.getRun(lastCommit, "Build and test") ?: run {
            val text = "${title}${time} \nArtifact does not exist $PLEADING_FACE (expired or first pr of contributor)"
            reply(embed(embedTitle, text, readColor(pr)))
            return
        }

        if (job.startedAt?.let { toTimeMark(it).passedSince() > 90.days } == true) {
            reply(embed(embedTitle, "${title}${time} \nartifact has expired $PLEADING_FACE", readColor(pr)))
            return
        }

        if (job.status != Status.COMPLETED) {
            val text = when (job.status) {
                Status.REQUESTED -> "Run has been requested $PLEADING_FACE"
                Status.QUEUED -> "Run is in queue $PLEADING_FACE"
                Status.IN_PROGRESS -> "Run is in progress $PLEADING_FACE"
                Status.WAITING -> "Run is waiting $PLEADING_FACE"
                Status.PENDING -> "Run is pending $PLEADING_FACE"
                else -> ""
            }
            reply(embed(embedTitle, "${title}${time} \n $text", readColor(pr)))
            return
        }

        val match = job.htmlUrl?.let { runIdRegex.matchEntire(it) }
        val runId = match?.groups?.get("RunId")?.value

        val artifactLink = "$BASE/actions/runs/$runId?pr=$prNumber"
        val nightlyLink = "https://nightly.link/$USER/$REPO/actions/runs/$runId/Development%20Build.zip"
        val artifactLine = "GitHub".linkTo(artifactLink)
        val nightlyLine = "Nightly".linkTo(nightlyLink)

        val artifactDisplay = buildString {
            append(" \n")
            append("Download the latest development build of this pr!")
            append("\n")
            append("> From $artifactLine (requires a GitHub Account)")
            append("\n")
            append("> From $nightlyLine (unofficial)")
            append("\n")
            append("> (updated ${passedSince(job.completedAt ?: "")})")
        }

        reply(embed(embedTitle, "$title$time$artifactDisplay", readColor(pr)))
    }

    // Colors picked from GitHub
    private fun readColor(pr: PullRequestJson): Color = when {
        pr.draft -> Color(101, 108, 118)
        pr.merged -> Color(130, 86, 208)
        else -> Color(52, 125, 57)
    }

    private fun parseToUnixTime(isoTimestamp: String): Long =
        Instant.from(DateTimeFormatter.ISO_INSTANT.parse(isoTimestamp)).epochSecond

    private fun toTimeMark(stringTime: String): SimpleTimeMark = (parseToUnixTime(stringTime) * 1000).asTimeMark()

    private fun passedSince(stringTime: String): String = "<t:${parseToUnixTime(stringTime)}:R>"

    private fun releaseSinceMerge(stringTimeMerge: String, stringTimeLastRelease: String): Boolean {
        val timeMerge = parseToUnixTime(stringTimeMerge)
        val timeLastRelease = parseToUnixTime(stringTimeLastRelease)
        return timeMerge < timeLastRelease
    }

    @Suppress("unused") // TODO implement once we can upload the file
    private fun MessageReceivedEvent.pullRequestArtifactCommand(args: List<String>) {
        if (args.size != 2) {
            reply("Usage: `!prupload <number>`")
            return
        }
        val prNumber = args[1].toIntOrNull() ?: run {
            reply("unknwon number $PLEADING_FACE (${args[1]})")
            return
        }

        val prLink = "$BASE/pull/$prNumber"
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

        val displayUrl = "$BASE/actions/runs/$artifactId?pr=$prNumber"

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

    fun isPullRequest(event: MessageReceivedEvent, message: String): Boolean {
        val matcher = pullRequestPattern.matcher(message)
        if (!matcher.matches()) return false
        val pr = matcher.group("pr")?.toIntOrNull() ?: return false
        event.replyWithConsumer("Next time just type `!pr $pr` $PLEADING_FACE") { consumer ->
            runDelayed(10.seconds) {
                consumer.message.messageDelete()
            }
        }
        event.loadPrInfos(pr)
        return true
    }

}