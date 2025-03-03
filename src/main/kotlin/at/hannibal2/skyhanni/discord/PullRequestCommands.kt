package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.createParentDirIfNotExist
import at.hannibal2.skyhanni.discord.Utils.format
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.timeExecution
import at.hannibal2.skyhanni.discord.Utils.uploadFile
import at.hannibal2.skyhanni.discord.github.GitHubClient
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.io.File

class PullRequestCommands(private val config: BotConfig, commands: Commands) {

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
        reply("Looking for pr <$prLink> ..")

        val pr = github.findPullRequest(prNumber) ?: run {
            reply("pr is null!")
            return
        }
        val head = pr.head
        val title = pr.title
        val author = head.user.login

        val lastCommit = head.sha

        reply("found pr `$title` (from `$author`)")

        reply("looking for artifact ..")
        val artifact = github.findArtifact(lastCommit) ?: run {
            reply("artifact is null!")
            return
        }
        reply("found artifact")

        val artifactId = artifact.id
        val fileRaw = File("temp/downloads/artifact-$artifactId-raw")
        fileRaw.createParentDirIfNotExist()
        val fileUnzipped = File("temp/downloads/artifact-$artifactId")
        reply("Downloading artifact ..")
        val (_, downloadTime) = timeExecution {
            try {
                github.downloadArtifact(artifactId, fileRaw)
            } catch (e: Throwable) {
                e.printStackTrace()
                reply("error while downloading artifact: ${e.message}")
                return
            }
        }
        reply("artifact fully downnloaded, took ${downloadTime.format()}")

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
        reply("artifact fully downnloaded, took ${uploadTime.format()}")
    }

    fun findJarFile(directory: File): File? {
        return directory.walkTopDown().firstOrNull { it.isFile && it.name.startsWith("SkyHanni-") }
    }
}
