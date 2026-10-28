package at.hannibal2.skyhanni.discord.command

import at.hannibal2.changelog.ModVersion
import at.hannibal2.skyhanni.discord.*
import at.hannibal2.skyhanni.discord.Utils.format
import at.hannibal2.skyhanni.discord.Utils.hasRoleOrAbove
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyLong
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.changelog.ChangelogGenerator
import at.hannibal2.skyhanni.discord.github.GitHubClient
import at.hannibal2.skyhanni.discord.utils.ErrorManager.handleError
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

@Suppress("unused")
object ChangelogCommand : BaseCommand() {

    override val name: String = "changelog"
    override val description: String = "Generates the changelog of the next beta, or of the latest one for the announcement."
    override val options: List<Option> = listOf(
        Option("target", "preview (default) or announcement.", required = false, autoComplete = true),
    )

    override val requiredRole: PermissionRole = PermissionRole.CODE_REVIEWER

    /** Shown next to the text length, the text limit Discord sets for a message with components. */
    private const val MESSAGE_LIMIT = 4000

    private val github = GitHubClient("SkyHanniStudios", "SkyHanni", BOT.config.githubTokenPullRequests)
    private val cooldown = 2.minutes
    private val tagPattern = "\\d+\\.\\d+\\.\\d+".toPattern()

    private val running = AtomicBoolean(false)

    // set once the builder process runs, aborts before that leave the cooldown untouched
    @Volatile
    private var lastStart = SimpleTimeMark.farPast()

    private enum class Target(val label: String) {
        PREVIEW("preview"),
        ANNOUNCEMENT("announcement"),
    }

    override fun autoCompleteChoices(optionName: String, input: String): List<String> =
        Target.entries.map { it.label }.filter { it.contains(input, ignoreCase = true) }

    override fun CommandEvent.execute(args: List<String>) {
        if (args.size > 1) return wrongUsage("[preview|announcement]")
        val input = args.firstOrNull()?.lowercase() ?: Target.PREVIEW.label
        val target = Target.entries.find { it.label == input } ?: return wrongUsage("[preview|announcement]")

        if (target == Target.ANNOUNCEMENT && member?.hasRoleOrAbove(PermissionRole.ADMIN) != true) {
            return userError("Only admins can generate the announcement.")
        }

        if (!running.compareAndSet(false, true)) return reply("A changelog is already being generated $PLEADING_FACE")
        val passed = lastStart.passedSince()
        if (passed < cooldown) {
            running.set(false)
            return userError("Please wait ${(cooldown - passed).format()} before generating the next changelog.")
        }

        logAction("generates the changelog (${target.label})")
        Utils.runAsync("changelog ${target.label}") {
            try {
                generate(target)
            } catch (e: Throwable) {
                // the bot channel still gets the error when the reply itself throws
                try {
                    reply("Error: ${e.message}")
                } finally {
                    e.handleError("Discord command: `$name`", "Target: ${target.label}")
                }
            } finally {
                running.set(false)
            }
        }
    }

    private fun CommandEvent.generate(target: Target) {
        val tag = github.getLatestTag().name
        if (!tagPattern.matcher(tag).matches()) error("Unexpected tag name `$tag`")
        val latest = ModVersion.fromString(tag)

        val (version, previousVersion) = when (target) {
            Target.PREVIEW -> ModVersion(latest.stable, latest.beta + 1, bugfix = 0) to null
            Target.ANNOUNCEMENT -> {
                if (!latest.isBeta || latest.bugfix != 0) {
                    return userError("The latest tag `$tag` is no x.y.0 beta release, there is nothing to announce.")
                }
                latest to latest
            }
        }

        val result = ChangelogGenerator.generate(version, previousVersion) { lastStart = SimpleTimeMark.now() }
        replyLong(buildReply(version, target, result))
    }

    // built as one string so a single replyLong keeps the order, its first part replaces the deferred placeholder
    private fun buildReply(version: ModVersion, target: Target, result: ChangelogGenerator.Result): String =
        buildString {
            appendLine("**SkyHanni ${version.asTitle}** (${target.label})")
            appendLine("Length: ${result.text.length}/$MESSAGE_LIMIT")
            if (result.messages.isNotEmpty()) {
                appendLine()
                result.messages.forEach { appendLine(it) }
            }
            appendLine()
            result.summary.forEach { appendLine(it) }
            appendLine()
            append(result.text)
        }
}
