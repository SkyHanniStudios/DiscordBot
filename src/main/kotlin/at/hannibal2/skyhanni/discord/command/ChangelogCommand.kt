package at.hannibal2.skyhanni.discord.command

import at.hannibal2.changelog.ModVersion
import at.hannibal2.changelog.PullRequestCategory
import at.hannibal2.skyhanni.discord.*
import at.hannibal2.skyhanni.discord.Utils.format
import at.hannibal2.skyhanni.discord.Utils.hasRoleOrAbove
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.replyLong
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.changelog.ChangelogGenerator
import at.hannibal2.skyhanni.discord.changelog.ChangelogMessages
import at.hannibal2.skyhanni.discord.changelog.ChangelogTarget
import at.hannibal2.skyhanni.discord.changelog.ChangelogWebhook
import at.hannibal2.skyhanni.discord.command.ChangelogCommand.post
import at.hannibal2.skyhanni.discord.github.GitHubClient
import at.hannibal2.skyhanni.discord.utils.ErrorManager.handleError
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

@Suppress("unused")
object ChangelogCommand : BaseCommand() {

    override val name: String = "changelog"
    override val description: String =
        "Posts the next beta changelog as preview, or the latest beta changelog as announcement."
    override val options: List<Option> = listOf(
        Option("target", "preview (default) or announcement.", required = false, autoComplete = true),
    )

    override val requiredRole: PermissionRole = PermissionRole.CODE_REVIEWER

    /** Discord's text limit for a message with components, a longer changelog is not posted. */
    private const val MESSAGE_LIMIT = 4000

    private val github = GitHubClient("SkyHanniStudios", "SkyHanni", BOT.config.githubTokenPullRequests)
    private val cooldown = 1.minutes
    private val tagPattern = "\\d+\\.\\d+\\.\\d+".toPattern()

    private val running = AtomicBoolean(false)

    // set once the builder process runs, aborts before that leave the cooldown untouched
    @Volatile
    private var lastStart = SimpleTimeMark.farPast()

    /** What [post] did: the status lines for the reply, and the posted or edited message if there is one. */
    private data class PostOutcome(
        val status: String,
        val message: ChangelogWebhook.PostedMessage? = null,
        val threadStatus: String? = null,
    )

    override fun autoCompleteChoices(optionName: String, input: String): List<String> =
        ChangelogTarget.entries.map { it.label }.filter { it.contains(input, ignoreCase = true) }

    override fun CommandEvent.execute(args: List<String>) {
        if (args.size > 1) return wrongUsage("[preview|announcement]")
        val input = args.firstOrNull()?.lowercase() ?: ChangelogTarget.PREVIEW.label
        val target = ChangelogTarget.entries.find { it.label == input } ?: return wrongUsage("[preview|announcement]")

        if (target == ChangelogTarget.ANNOUNCEMENT && member?.hasRoleOrAbove(PermissionRole.ADMIN) != true) {
            return userError("Only admins can post the announcement.")
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

    private fun CommandEvent.generate(target: ChangelogTarget) {
        val tag = github.getLatestTag().name
        if (!tagPattern.matcher(tag).matches()) error("Unexpected tag name `$tag`")
        val latest = ModVersion.fromString(tag)

        val (version, previousVersion) = when (target) {
            ChangelogTarget.PREVIEW -> ModVersion(latest.stable, latest.beta + 1, bugfix = 0) to null
            ChangelogTarget.ANNOUNCEMENT -> {
                if (!latest.isBeta || latest.bugfix != 0) {
                    return userError("The latest tag `$tag` is no x.y.0 beta release, there is nothing to announce.")
                }
                latest to latest
            }
        }

        val result = ChangelogGenerator.generate(version, previousVersion) { lastStart = SimpleTimeMark.now() }
        val outcome = try {
            post(target, version, result)
        } catch (e: Exception) {
            e.handleError("Discord command: `$name`", "Posting to ${target.label}")
            PostOutcome("Error while posting: ${e.message}")
        }
        replyLong(buildReply(version, target, result, outcome))
    }

    /** Posts or edits the message of [target] and says what happened. */
    private fun CommandEvent.post(
        target: ChangelogTarget,
        version: ModVersion,
        result: ChangelogGenerator.Result
    ): PostOutcome {
        val text = target.postedText(result.text)
        if (text.length > MESSAGE_LIMIT) {
            return PostOutcome("Not posted: the text is longer than $MESSAGE_LIMIT characters.")
        }
        if (target == ChangelogTarget.ANNOUNCEMENT && result.hasBlockingErrors) {
            return PostOutcome("Not posted: a pull request has a wrong title or a broken description, see below.")
        }
        val webhookUrl = target.webhookUrl
            ?: return PostOutcome("Not posted: `${target.configKey}` in config.json is missing or no https url.")

        val previous = ChangelogMessages.get(target)?.takeIf { it.version == version.toString() }
        val edited = previous?.let { ChangelogWebhook.edit(webhookUrl, it.messageId, text) }
        if (previous != null && edited != null) {
            val threadStatus = when {
                target != ChangelogTarget.PREVIEW -> null
                previous.threadMessageIds == null -> "Thread: skipped, the message was posted before threads existed"
                else -> postThread(version, webhookUrl, edited, previous.threadMessageIds, result)
            }
            return PostOutcome("Edited", edited, threadStatus)
        }

        val posted = ChangelogWebhook.send(webhookUrl, text)
        try {
            ChangelogMessages.set(target, ChangelogMessages.Entry(posted.messageId, version.toString()))
        } catch (e: Exception) {
            // the message exists, so the reply still links it, even when the bot channel is unreachable as well
            runCatching {
                e.handleError("Discord command: `$name`", "Storing the ${target.label} message id", "Posted: ${link(posted)}")
            }
            return PostOutcome("Posted, but the message id could not be stored, the next run posts a new message", posted)
        }
        val threadStatus =
            if (target == ChangelogTarget.PREVIEW) postThread(version, webhookUrl, posted, null, result) else null
        if (previous == null) return PostOutcome("Posted", posted, threadStatus)

        val botChannelText = "The ${target.label} changelog message was deleted, posted a new one: ${link(posted)}"
        try {
            Utils.sendMessageToBotChannel(botChannelText)
        } catch (e: Exception) {
            // an unreachable bot channel must not turn the successful post into an error
            BOT.logger.warn("Could not report the repost of the ${target.label} changelog in the bot channel", e)
        }
        return PostOutcome("The old message was deleted, posted a new one", posted, threadStatus)
    }

    /**
     * Posts or edits one message per category in the thread of the preview message [main] and returns the status line.
     * Null [threadMessageIds] starts that thread first.
     */
    private fun postThread(
        version: ModVersion,
        webhookUrl: String,
        main: ChangelogWebhook.PostedMessage,
        threadMessageIds: Map<String, String>?,
        result: ChangelogGenerator.Result,
    ): String = try {
        if (threadMessageIds == null) {
            val channel = BOT.jda.getTextChannelById(main.channelId) ?: error("Channel ${main.channelId} not found")
            channel.createThreadChannel("SkyHanni ${version.asTitle}", main.messageId).complete()
        }
        // a thread has the id of the message it was started from
        val threadUrl = ChangelogWebhook.buildUrl(webhookUrl, "", "thread_id=${main.messageId}")
        val ids = threadMessageIds.orEmpty().toMutableMap()
        var edited = 0
        var posted = 0
        val tooLong = mutableListOf<String>()
        // empty categories get a placeholder, so a later run edits them in place
        for (category in PullRequestCategory.entries) {
            val text = result.internalSections[category] ?: "### ${category.changelogName}\n-# no changes"
            if (text.length > MESSAGE_LIMIT) {
                tooLong.add(category.changelogName)
                continue
            }
            if (ids[category.name]?.let { ChangelogWebhook.edit(threadUrl, it, text) } != null) {
                edited++
                continue
            }
            ids[category.name] = ChangelogWebhook.send(threadUrl, text).messageId
            posted++
            // stored after every message, an error later in the loop keeps the ids posted so far
            val entry = ChangelogMessages.Entry(main.messageId, version.toString(), ids.toMap())
            ChangelogMessages.set(ChangelogTarget.PREVIEW, entry)
        }
        val parts = listOfNotNull(
            "$edited edited".takeIf { edited > 0 },
            "$posted posted".takeIf { posted > 0 },
            "longer than $MESSAGE_LIMIT characters: ${tooLong.joinToString()}".takeIf { tooLong.isNotEmpty() },
        )
        "Thread: ${parts.joinToString(", ")}"
    } catch (e: Exception) {
        e.handleError("Discord command: `$name`", "Thread of the preview message")
        "Thread: failed: ${e.message}"
    }

    private fun CommandEvent.link(message: ChangelogWebhook.PostedMessage): String =
        "https://discord.com/channels/${guild.id}/${message.channelId}/${message.messageId}"

    // built as one string so a single replyLong keeps the order, its first part replaces the deferred placeholder
    private fun CommandEvent.buildReply(
        version: ModVersion,
        target: ChangelogTarget,
        result: ChangelogGenerator.Result,
        outcome: PostOutcome,
    ): String = buildString {
        // the builder messages carry pull request urls, <> keeps Discord from adding a preview for each
        val url = "(?<!<)https?://[^\\s)>`]*[^\\s)>`.,?!]".toRegex()
        appendLine("**SkyHanni ${version.asTitle}** (${target.label})")
        appendLine(outcome.status)
        outcome.threadStatus?.let { appendLine(it) }
        appendLine("Length: ${target.postedText(result.text).length}/$MESSAGE_LIMIT")
        if (result.messages.isNotEmpty()) {
            appendLine()
            result.messages.forEach { appendLine(it.replace(url, "<$0>")) }
        }
        appendLine()
        result.summary.forEach { appendLine(it) }
        outcome.message?.let {
            appendLine()
            append(link(it))
        }
    }
}
