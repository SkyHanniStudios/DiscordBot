package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.createParentDirIfNotExist
import at.hannibal2.skyhanni.discord.Utils.format
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.timeExecution
import at.hannibal2.skyhanni.discord.Utils.uploadFile
import at.hannibal2.skyhanni.discord.github.GitHubClient
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.io.File

@Suppress("ReturnCount")
class PullRequestCommands(config: BotConfig, commands: Commands) {

    private val github = GitHubClient("hannibal002", "SkyHanni", config.githubToken)

    init {
        commands.add(Command("pr") { event, args -> event.pullRequestCommand(args) })
    }

    private fun MessageReceivedEvent.pullRequestCommand(args: List<String>) {
        if (args.size != 2) {
            reply("Usage: `!pr <number>`")
            return
        }
        val prNumber = args[1].toIntOrNull() ?: run {
            reply("unknwon number \uD83E\uDD7A (${args[1]})")
            return
        }

        val prLink = "https://github.com/hannibal002/SkyHanni/pull/$prNumber"

        val pr = try {
            github.findPullRequest(prNumber) ?: run {
                reply("pr is null!")
                return
            }
        } catch (e: IllegalStateException) {
            if (e.message == "GitHub API error: 404") {
                val issueUrl = "https://github.com/hannibal002/SkyHanni/issues/123"
                val issue = "issue".linkTo(issueUrl)
                reply("This pull request does not yet exist or is an $issue \uD83E\uDD7A")
                return
            }
            reply("error while finding pull request: ${e.message}")
            return
        }
        val updatedAt = pr.updatedAt
        val head = pr.head
        val userName = head.user.login
        val userProfile = "https://github.com/$userName"
        val prNumberDisplay = "#$prNumber".linkTo(prLink)
        val userNameDisplay = userName.linkTo(userProfile)
        val title = "`${pr.title}` ($prNumberDisplay by $userNameDisplay)\n" + "updatedAt=$updatedAt"

        val lastCommit = head.sha
        val artifact = github.findArtifact(lastCommit) ?: run {
            reply("$title\nLatest artifact could not be found \uD83E\uDD7A (expired or not yet compiled)")
            return
        }

        val runId = artifact.workflowRun.id
        val artifactLink = "https://github.com/hannibal002/SkyHanni/actions/runs/$runId?pr=$prNumber"
        val artifactLine = "Download development build".linkTo(artifactLink)
        reply("$title\n$artifactLine")
    }

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
        reply("Looking for pr <$prLink> ..")

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
