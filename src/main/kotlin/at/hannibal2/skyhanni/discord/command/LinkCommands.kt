package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.Database
import at.hannibal2.skyhanni.discord.OPEN_PR_TAG
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.Utils.userSuccess
import at.hannibal2.skyhanni.discord.command.LinkCommand.setTags
import at.hannibal2.skyhanni.discord.command.LinkCommand.setTitle
import net.dv8tion.jda.api.entities.channel.ChannelType
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
        val first = args.first()
        val prNumber = first.toIntOrNull() ?: run {
            userError("Unknown number $PLEADING_FACE ($first})")
            return
        }
        if (prNumber < 1) {
            userError("PR number needs to be positive $PLEADING_FACE")
            return
        }

        if (!isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            userError("Wrong channel $PLEADING_FACE")
            return
        }

        val post = channel.asThreadChannel()
        val manager = post.manager

        if (Database.isLinked(post.id)) {
            reply("Post already linked to ${Database.getPullrequest(channel.id)} $PLEADING_FACE")
            return
        }

        Database.addLink(post.id, prNumber)
        logAction("${author.name} linked pr $prNumber")

        if (!post.name.contains("(PR #")) manager.setTitle("${post.name} (PR #$prNumber)")

        val tags = post.appliedTags
        if (tags.none { it.id == OPEN_PR_TAG }) {
            val tag = post.parentChannel.asForumChannel().getAvailableTagById(OPEN_PR_TAG) ?: return
            manager.setTags(tags + tag)
        }

        userSuccess("Successfully linked PR $prNumber to this post.")
    }

    fun ThreadChannelManager.setTags(tags: List<ForumTag>) {
        setAppliedTags(tags).queue()
    }

    fun ThreadChannelManager.setTitle(name: String) {
        setName(name).queue()
    }
}

object UnlinkCommand : BaseCommand() {
    override val name = "unlink"

    override val description = "Unlink a forum post from a pull request."

    override fun MessageReceivedEvent.execute(args: List<String>) {
        if (!isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            userError("Wrong channel $PLEADING_FACE")
            return
        }

        if (!Database.isLinked(channel.id)) {
            userError("Post isn't linked to any pull request $PLEADING_FACE")
            return
        }

        val post = channel.asThreadChannel()
        val manager = post.manager

        Database.deleteLink(post.id)
        logAction("${author.name} unlinked the pull request")

        if (post.name.contains("(PR #")) manager.setTitle(post.name.split("(PR #")[0])

        val tags = post.appliedTags
        if (tags.any { it.id == OPEN_PR_TAG }) {
            val tag = post.parentChannel.asForumChannel().getAvailableTagById(OPEN_PR_TAG) ?: return
            manager.setTags(tags.filter { it != tag })
        }

        userSuccess("Successfully unlinked this post.")
    }
}