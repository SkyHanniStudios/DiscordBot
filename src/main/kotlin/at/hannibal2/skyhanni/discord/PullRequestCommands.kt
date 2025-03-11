package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.SimpleTimeMark.Companion.asTimeMark
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
import at.hannibal2.skyhanni.discord.github.GitHubClient
import at.hannibal2.skyhanni.discord.json.discord.Conclusion
import at.hannibal2.skyhanni.discord.json.discord.PullRequestJson
import at.hannibal2.skyhanni.discord.json.discord.RunStatus
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.awt.Color
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Suppress("ReturnCount")
class PullRequestCommands(config: BotConfig, commands: CommandListener) {

    private val user = "hannibal002"
    private val repo = "SkyHanni"
    private val github = GitHubClient(user, repo, config.githubToken)
    private val base = "https://github.com/$user/$repo"

    init {
        commands.add(Command("pr", userCommand = true) { event, args -> event.pullRequestCommand(args) })
    }

    private val runIdRegex =
        Regex("https://github\\.com/[\\w.]+/[\\w.]+/actions/runs/(?<RunId>\\d+)/job/(?<JobId>\\d+)")
    private val pullRequestPattern = "$base/pull/(?<pr>\\d+)".toPattern()

    private fun MessageReceivedEvent.pullRequestCommand(args: List<String>) {
        if (args.size != 2) {
            reply("Usage: `!pr <number>`")
            return
        }
        val prNumber = args[1].removePrefix("#").toLongOrNull() ?: run {
            reply("unknown number $PLEADING_FACE (${args[1]})")
            return
        }
        if (prNumber < 1) {
            reply("PR number needs to be positive $PLEADING_FACE")
            return
        }
        loadPrInfos(prNumber)
    }

    private fun MessageReceivedEvent.loadPrInfos(prNumber: Long) {
        logAction("loads pr infos for #$prNumber")

        val prLink = "$base/pull/$prNumber"

        val pr = try {
            github.findPullRequest(prNumber) ?: run {
                reply("pr is null!")
                return
            }
        } catch (e: IllegalStateException) {
            if (e.message?.contains(" code:404 ") == true) {
                val issueUrl = "$base/issues/$prNumber"
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

        val labels = pr.labels.map { it.name }.toSet()

        val time = buildString {
            val lastUpdate = passedSince(pr.updatedAt)
            val created = passedSince(pr.createdAt)
            append("> Created: $created")
            append("\n")
            append("> Last Updated: $lastUpdate")
            append("\n")
            appendLabelCategory("Type", labels, this)
            appendLabelCategory("State", labels, this)
            appendLabelCategory("Milestone", labels, this,
                if (pr.milestone != null) " `${pr.milestone.title}`" else "")
        }

        if (toTimeMark(pr.updatedAt).passedSince() > 400.days) {
            val text = "${title}${time} \nBuild data no longer exists $PLEADING_FACE"
            reply(embed(embedTitle, text, readColor(pr)))
            return
        }

        val lastCommit = head.sha

        val job = github.getRun(lastCommit, "Build and test") ?: run {
            val text = "${title}${time} \nBuild needs approval $PLEADING_FACE"

            reply(embed(embedTitle, text, readColor(pr)))
            return
        }

        if (job.startedAt?.let { toTimeMark(it).passedSince() > 90.days } == true) {
            reply(embed(embedTitle, "${title}${time} \nBuild download has expired $PLEADING_FACE", readColor(pr)))
            
            return
        }

        if (job.status != RunStatus.COMPLETED) {
            val text = when (job.status) {
                RunStatus.REQUESTED -> "Build has been requested $PLEADING_FACE"
                RunStatus.QUEUED -> "Build is in queue $PLEADING_FACE"
                RunStatus.IN_PROGRESS -> "Build is in progress $PLEADING_FACE"
                RunStatus.WAITING -> "Build is waiting $PLEADING_FACE"
                RunStatus.PENDING -> "Build is pending $PLEADING_FACE"
                else -> ""
            }
            reply(embed(embedTitle, "${title}${time} \n $text", readColor(pr)))
            return
        }

        if (job.conclusion != Conclusion.SUCCESS) {
            reply(embed(embedTitle, "$title$time\nLast development build failed $PLEADING_FACE", Color.red))
            return
        }

        val match = job.htmlUrl?.let { runIdRegex.matchEntire(it) }
        val runId = match?.groups?.get("RunId")?.value

        val artifactLink = "$base/actions/runs/$runId?pr=$prNumber"
        val nightlyLink = "https://nightly.link/$user/$repo/actions/runs/$runId/Development%20Build.zip"
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

    private val labelTypes: Map<String, Set<String>> = mapOf(
        Pair("Type", setOf("Backend", "Bug Fix")),
        Pair("State", setOf("Detekt", "Merge Conflicts", "Waiting on Dependency PR", "Waiting on Hypixel", "Wrong Title/Changelog")),
        Pair("Milestone", setOf("Soon")),
        Pair("Misc", setOf("Good First Issue"))
    )

    private fun appendLabelCategory(labelType: String, labels: Set<String>, stringBuilder: StringBuilder, suffix: String = ""): StringBuilder {
        val labelsWithType = labels.intersect(labelTypes[labelType] ?: setOf())
        if (labelsWithType.isEmpty()) return stringBuilder.append(if (suffix.isNotEmpty()) "> $labelType: $suffix\n" else "")
        return stringBuilder.append("> $labelType: `${labelsWithType.joinToString("` `")}`$suffix\n")
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
        val prNumber = args[1].toLongOrNull() ?: run {
            reply("unknwon number $PLEADING_FACE (${args[1]})")
            return
        }

        val prLink = "$base/pull/$prNumber"
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

        val displayUrl = "$base/actions/runs/$artifactId?pr=$prNumber"

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
        val pr = matcher.group("pr")?.toLongOrNull() ?: return false
        event.replyWithConsumer("Next time just type `!pr $pr` $PLEADING_FACE") { consumer ->
            runDelayed(10.seconds) {
                consumer.message.messageDelete()
            }
        }
        event.loadPrInfos(pr)
        return true
    }

}
