package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.SimpleTimeMark
import at.hannibal2.skyhanni.discord.SimpleTimeMark.Companion.asTimeMark
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.createParentDirIfNotExist
import at.hannibal2.skyhanni.discord.Utils.doWhen
import at.hannibal2.skyhanni.discord.Utils.embed
import at.hannibal2.skyhanni.discord.Utils.format
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.messageDelete
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyWithConsumer
import at.hannibal2.skyhanni.discord.Utils.runDelayed
import at.hannibal2.skyhanni.discord.Utils.timeExecution
import at.hannibal2.skyhanni.discord.Utils.unzipFile
import at.hannibal2.skyhanni.discord.Utils.uploadFile
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.github.GitHubClient
import at.hannibal2.skyhanni.discord.json.discord.PullRequestJson
import at.hannibal2.skyhanni.discord.json.discord.Status
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
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

    override fun execute(args: List<String>, event: Any) {
        val prNumber = doWhen(event, {
            if (args.size != 1) {
                wrongUsage("<number>", event)
                return@doWhen null
            }

            val first = args.first()
            first.toIntOrNull() ?: run {
                userError("Unknown number $PLEADING_FACE ($first})", event)
                null
            }
        }, {
            it.getOption("number")?.asInt
        }) ?: return

        if (prNumber < 1) {
            userError("PR number needs to be positive $PLEADING_FACE", event, ephemeral = true)
            return
        }

        loadPrInfos(prNumber, event)
    }

    private fun loadPrInfos(prNumber: Int, event: Any) {
        when (event) {
            is MessageReceivedEvent -> logAction("loads pr infos for #$prNumber", event)
            is SlashCommandInteractionEvent -> logAction("loads pr infos for #$prNumber", event)
        }

        val prLink = "$BASE/pull/$prNumber"

        val pr =
            runCatching { github.findPullRequest(prNumber) }.getOrElse { handlePrError(prNumber, event, it); return }
                ?: return

        val head = pr.head
        val userName = head.user.login
        val prNumberDisplay = "#$prNumber".linkTo(prLink)
        val userNameDisplay = userName.linkTo("https://github.com/$userName")
        val embedTitle = pr.title
        val title = "> $prNumberDisplay by $userNameDisplay\n"
        val time = "> Created: ${passedSince(pr.createdAt)}\n> Last Updated: ${passedSince(pr.updatedAt)}\n"

        val job = github.getRun(head.sha, "Build and test") ?: run {
            reply(
                embed(
                    embedTitle,
                    "$title$time \nArtifact does not exist $PLEADING_FACE (expired or first PR of contributor)",
                    readColor(pr)
                ),
                event
            )
            return
        }

        if (job.startedAt?.let { toTimeMark(it).passedSince() > 90.days } == true) {
            reply(embed(embedTitle, "$title$time \nArtifact has expired $PLEADING_FACE", readColor(pr)), event)
            return
        }

        if (job.status != Status.COMPLETED) {
            val statusText = mapOf(
                Status.REQUESTED to "Run has been requested $PLEADING_FACE",
                Status.QUEUED to "Run is in queue $PLEADING_FACE",
                Status.IN_PROGRESS to "Run is in progress $PLEADING_FACE",
                Status.WAITING to "Run is waiting $PLEADING_FACE",
                Status.PENDING to "Run is pending $PLEADING_FACE"
            )[job.status] ?: ""
            reply(embed(embedTitle, "$title$time \n$statusText", readColor(pr)), event)
            return
        }

        val runId = job.htmlUrl?.let { runIdRegex.matchEntire(it)?.groups?.get("RunId")?.value }
        val artifactLink = "$BASE/actions/runs/$runId?pr=$prNumber"
        val nightlyLink = "https://nightly.link/$USER/$REPO/actions/runs/$runId/Development%20Build.zip"
        val artifactDisplay =
            "\nDownload the latest development build of this PR!\n> From ${"GitHub".linkTo(artifactLink)} (requires a GitHub Account)\n> From ${
                "Nightly".linkTo(nightlyLink)
            } (unofficial)\n> (updated ${passedSince(job.completedAt ?: "")})"

        reply(embed(embedTitle, "$title$time$artifactDisplay", readColor(pr)), event)
    }

    private fun handlePrError(prNumber: Int, event: Any, e: Throwable) {
        if (e is IllegalStateException && e.message?.contains(" code:404 ") == true) {
            val text = "This pull request does not yet exist or is an ${"issue".linkTo("$BASE/issues/$prNumber")}"
            reply(embed("Not found $PLEADING_FACE", text, Color.red), event, ephemeral = true)
            return
        }
        reply("Could not load pull request infos for #$prNumber: ${e.message}", event, ephemeral = true)
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

    @Suppress("unused") // TODO implement once we can upload the file
    private fun MessageReceivedEvent.pullRequestArtifactCommand(args: List<String>) {
        if (args.size != 2) {
            reply("Usage: `!prupload <number>`", this)
            return
        }
        val prNumber = args[1].toIntOrNull() ?: run {
            reply("unknwon number $PLEADING_FACE (${args[1]})", this)
            return
        }

        val prLink = "$BASE/pull/$prNumber"
        reply("Looking for pr <$prLink..", this)

        val pr = github.findPullRequest(prNumber) ?: run {
            reply("pr is null!", this)
            return
        }
        val head = pr.head
        reply("found pr `${pr.title}` (from `${head.user.login}`)", this)

        reply("looking for artifact ..", this)
        val lastCommit = head.sha
        val artifact = github.findArtifact(lastCommit) ?: run {
            reply("artifact is null!", this)
            return
        }
        reply("found artifact", this)

        val artifactId = artifact.id
        val fileRaw = File("temp/downloads/artifact-$artifactId-raw")
        fileRaw.createParentDirIfNotExist()
        val fileUnzipped = File("temp/downloads/artifact-$artifactId-unzipped")
        reply("Downloading artifact ..", this)
        val (_, downloadTime) = timeExecution {
            github.downloadArtifact(artifactId, fileRaw)
        }
        reply("artifact downnloaded in ${downloadTime.format()}", this)

        unzipFile(fileRaw, fileUnzipped)
        fileRaw.delete()

        val displayUrl = "$BASE/actions/runs/$artifactId?pr=$prNumber"

        val modJar = findJarFile(fileUnzipped) ?: run {
            reply("mod jar not found!", this)
            return
        }

        reply("start uploading..", this)
        val (_, uploadTime) = timeExecution {
            channel.uploadFile(modJar, "here is the jar from <$displayUrl>")
        }
        reply("Mod jar uploaded in ${uploadTime.format()}", this)
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
        loadPrInfos(pr, this)
        return true
    }
}

//object SlashPullRequestCommand : SlashCommand() {
//    override val name = "pr"
//    override val description = "Displays useful information about a pull request on GitHub."
//    override val options: List<SlashOption> = listOf(
//        SlashOption("number", "Number of the pull request you want to display.", type = OptionType.INTEGER)
//    )
//
//    override val userCommand: Boolean = true
//
//    override fun SlashCommandInteractionEvent.execute() {
//        getOption("number")?.let {
//            val prNumber = it.asInt
//            if (prNumber < 1) {
//                replyT("PR number needs to be positive $PLEADING_FACE")
//                return
//            }
//
//            loadPrInfos(prNumber, this)
//        }
//    }
//}