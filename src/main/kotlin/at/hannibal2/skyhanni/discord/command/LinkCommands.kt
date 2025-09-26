package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.Database
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.Utils.userSuccess
import at.hannibal2.skyhanni.discord.command.LinkCommand.setTags
import at.hannibal2.skyhanni.discord.command.LinkCommand.setTitle
import at.hannibal2.skyhanni.discord.command.PullRequestCommand.parseValidPrNumber
import at.hannibal2.skyhanni.discord.github.GitHubClient
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.forums.ForumTag
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager

object LinkCommand : BaseCommand() {
    override val name = "link"

    override val description = "Link a forum post to a pull request."
    override val options: List<Option> = listOf(
        Option("number", "Number of the pull request you want the post to be linked to.")
    )

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (args.size != 1) return wrongUsage("<number>")
        val prNumber = parseValidPrNumber(args.first()) ?: return

        if (!isValidPrNumber(prNumber)) return

        val post = channel.asThreadChannel()
        val manager = post.manager

        Database.getPullrequest(channel.id)?.let {
            reply("Post already linked to $it $PLEADING_FACE")
            return
        }

        Database.addLink(post.id, prNumber)
        logAction("linked pr #$prNumber to this channel")

        val titleFormat = prNumber.titleFormat()
        if (!post.name.contains(titleFormat)) {
            manager.setTitle("${post.name}$titleFormat")
        }

        post.updateTags(true)

        userSuccess("Successfully linked PR $prNumber to this post.")
    }

    fun ThreadChannelManager.setTags(tags: List<ForumTag>) {
        setAppliedTags(tags).queue()
    }

    fun ThreadChannelManager.setTitle(name: String) {
        setName(name).queue()
    }

    private const val USER = "hannibal002"
    private const val REPO = "SkyHanni"
    private val github = GitHubClient(USER, REPO, BOT.config.githubTokenPullRequests)

    private fun MessageReceivedEvent.isValidPrNumber(number: Int): Boolean {
        if (!isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            userError("Wrong channel $PLEADING_FACE")
            return false
        }

        try {
            github.findPullRequest(number) ?: run {
                userError("Pull request with number $number not found")
                return false
            }
        } catch (e: Exception) {
            userError("Pull request with number $number not found")
            return false
        }
        return true
    }
}

private fun ThreadChannel.updateTags(add: Boolean) {
    val tags = appliedTags
    val tag = parentChannel.asForumChannel().getAvailableTagById(BOT.config.openPrTagId) ?: return
    if (add && tag !in tags) tags.add(tag) else if (!add && tag in tags) tags.remove(tag)

    manager.setTags(tags)
}

private fun Int.titleFormat() = " (PR #$this)"

object UnlinkCommand : BaseCommand() {
    override val name = "unlink"

    override val description = "Unlink a forum post from a pull request."

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (!isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            userError("Wrong channel $PLEADING_FACE")
            return
        }

        val pr = Database.getPullrequest(channel.id) ?: run {
            userError("Post isn't linked to any pull request $PLEADING_FACE")
            return
        }

        val post = channel.asThreadChannel()
        val manager = post.manager

        Database.deleteLink(post.id)
        logAction("unlinked pr #$pr from this channel")

        val titleFormat = pr.titleFormat()
        if (post.name.contains(titleFormat)) {
            manager.setTitle(post.name.replace(titleFormat, ""))
        }

        post.updateTags(false)

        userSuccess("Successfully unlinked this post.")
    }
}