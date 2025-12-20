package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.*
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
import at.hannibal2.skyhanni.discord.Utils.userError
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

object PullRequestCommand : BaseCommand() {

    override val name: String = "pr"

    override val description: String = "Displays useful information about a pull request on Github."
    override val options: List<Option> = listOf(
        Option("number", "Number of the pull request you want to display.")
    )

    override val userCommand: Boolean = true

    private const val USER = "hannibal002"
    private const val REPO = "SkyHanni"
    private val github = GitHubClient(USER, REPO, BOT.config.githubTokenPullRequests)
    private const val BASE = "https://github.com/$USER/$REPO"

    private val runIdRegex =
        Regex("https://github\\.com/[\\w.]+/[\\w.]+/actions/runs/(?<RunId>\\d+)/job/(?<JobId>\\d+)")
    private val pullRequestPattern = "$BASE/pull/(?<pr>\\d+)".toPattern()
    private val cleanPullRequestPattern = "#(?<pr>\\d+),?".toPattern()

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (args.size != 1) return wrongUsage("<number>")
        val first = args.first().removePrefix("#")
        val prNumber = first.toIntOrNull() ?: run {
            userError("Unknown number $PLEADING_FACE ($first})")
            return
        }
        if (prNumber < 1) {
            userError("PR number needs to be positive $PLEADING_FACE")
            return
        }
        loadPrInfos(prNumber)
    }

    private fun MessageReceivedEvent.loadPrInfos(prNumber: Int, showError: Boolean = true) {
        logAction("loads pr infos for #$prNumber")

        val prLink = "$BASE/pull/$prNumber"

        val pr = try {
            github.findPullRequest(prNumber) ?: run {
                if (showError) {
                    reply("pr is null!")
                }
                return
            }
        } catch (e: IllegalStateException) {
            if (e.message?.contains(" code:404 ") == true) {
                val issueUrl = "$BASE/issues/$prNumber"
                val issue = "issue".linkTo(issueUrl)
                val text = "This pull request does not yet exist or is an $issue"
                if (showError) {
                    reply(embed("Not found $PLEADING_FACE", text, Color.red))
                }
                return
            }
            if (showError) {
                reply("Could not load pull request infos for #$prNumber: ${e.message}")
            }
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

        var inBeta = false

        val labels = pr.labels.map { it.name }.toSet()

        val time = buildString {
            val lastUpdate = passedSince(pr.updatedAt)
            val created = passedSince(pr.createdAt)
            append("> Created: $created")
            append("\n")
            if (!pr.merged) {
                append("> Last Updated: $lastUpdate")
                append("\n")
                appendLabelCategory("Type", labels, this)
                appendLabelCategory("State", labels, this)
                appendLabelCategory("Milestone", labels, this, pr.milestone?.let { " `${it.title}`" } ?: "")
            } else {
                val merged = passedSince(pr.mergedAt ?: "")
                append("> Merged: $merged")
                append("\n")

                val releases = try {
                    github.getReleases()
                } catch (e: Exception) {
                    null
                }

                val lastRelease = releases?.firstOrNull()

                if (releaseSinceMerge(pr.mergedAt ?: "", lastRelease?.publishedAt ?: "")) {
                    append("> This PR is in the latest beta $CHECK_MARK")
                    append("\n")
                    inBeta = true
                } else {
                    append("> This PR is not in the latest beta $BIG_X")
                    append("\n")
                }
            }
        }

        fun result(text: String, color: Color = readColor(pr)) {
            reply(embed(embedTitle, text, color, prLink))
        }

        if (toTimeMark(pr.updatedAt).passedSince() > 400.days && !inBeta) {
            result("${title}${time} \nBuild download has expired $PLEADING_FACE")
            return
        }

        val lastCommit = head.sha

        val job = github.getRun(lastCommit, "Build and test") ?: run {
            result(buildString {
                append(title)
                append(time)
                if (!inBeta) {
                    append("\n")
                    append("Build needs approval $PLEADING_FACE")
                }
            })
            return
        }

        if (job.startedAt?.let { toTimeMark(it).passedSince() > 90.days } == true && !inBeta) {
            result("${title}${time} \nBuild download has expired $PLEADING_FACE")
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

            result(buildString {
                append(title)
                append(time)
                if (!inBeta) {
                    append("\n")
                    append(text)
                }
            })
            return
        }

        if (job.conclusion != Conclusion.SUCCESS && !inBeta) {
            result("$title$time\nLast development build failed $PLEADING_FACE", Color.red)
            return
        }

        val match = job.htmlUrl?.let { runIdRegex.matchEntire(it) }
        val runId = match?.groups?.get("RunId")?.value

        val artifactLink = "$BASE/actions/runs/$runId?pr=$prNumber"
        fun nightlyLink(build: String) = "https://nightly.link/$USER/$REPO/actions/runs/$runId/$build%20Build.zip"
        val artifactLine = "GitHub".linkTo(artifactLink)
        val nightlyLine = "Nightly (1.21.5)".linkTo(nightlyLink("Development"))
        val latestNightlyLine = "Nightly (all versions)".linkTo(nightlyLink("Multi-version%20Development"))

        val artifactDisplay = buildString {
            append(" \n")
            append("Download the latest development build of this pr!")
            append("\n")
            append("> From $artifactLine (requires a GitHub Account)")
            append("\n")
            append("> From $nightlyLine (unofficial)")
            append("\n")
            append("> From $latestNightlyLine (unofficial)")
            append("\n")
            append("> (updated ${passedSince(job.completedAt ?: "")})")
        }

        result(buildString {
            append(title)
            append(time)
            if (!inBeta) {
                append(artifactDisplay)
            }
        })
    }

    private val labelTypes: Map<String, Set<String>> = mapOf(
        "Type" to setOf("Backend", "Bug Fix"), "State" to setOf(
            "Detekt",
            "Fails Multi-Version",
            "Merge Conflicts",
            "Waiting on Dependency PR",
            "Waiting on Hypixel",
            "Wrong Title/Changelog"
        ), "Milestone" to setOf("Soon"), "Misc" to setOf("Good First Issue")
    )

    private fun appendLabelCategory(
        labelType: String, labels: Set<String>, stringBuilder: StringBuilder, suffix: String = ""
    ): StringBuilder {
        val labelsWithType = labels.intersect(labelTypes[labelType] ?: setOf())
        if (labelsWithType.isEmpty()) return stringBuilder.append(if (suffix.isNotEmpty()) "> $labelType: $suffix\n" else "")
        return stringBuilder.append("> $labelType: `${labelsWithType.joinToString("` `")}`$suffix\n")
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
        val linkedMatcher = pullRequestPattern.matcher(message)
        if (linkedMatcher.matches()) {
            val pr = linkedMatcher.group("pr")?.toIntOrNull() ?: return false
            event.replyWithConsumer("Next time just type `!pr $pr` $PLEADING_FACE") { consumer ->
                runDelayed(10.seconds) {
                    consumer.message.messageDelete()
                }
            }
            event.loadPrInfos(pr)
            return true
        }

        val foundPrs = mutableListOf<Int>()
        for (line in message.split(" ")) {
            val matcher = cleanPullRequestPattern.matcher(line)
            if (matcher.matches()) {
                val pr = matcher.group("pr").toIntOrNull() ?: continue
                // we ignore too old prs, to avoid triggering on e.g. "#1 farming contest" messages
                if (pr < 800) continue
                foundPrs.add(pr)
            }
        }
        for (pr in foundPrs.take(3)) {
            event.loadPrInfos(pr, showError = false)
        }

        return false
    }

}
