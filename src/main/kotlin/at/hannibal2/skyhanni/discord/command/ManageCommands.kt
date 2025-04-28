package at.hannibal2.skyhanni.discord.command


import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.CHECK_MARK
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.SimpleTimeMark
import at.hannibal2.skyhanni.discord.Utils.embed
import at.hannibal2.skyhanni.discord.Utils.embedSend
import at.hannibal2.skyhanni.discord.Utils.getId
import at.hannibal2.skyhanni.discord.Utils.passedSince
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.command.ManageCommands.punishmentReply
import at.hannibal2.skyhanni.discord.command.ManageCommands.sendDM
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import java.awt.Color
import java.util.concurrent.TimeUnit

object ManageCommands {
    fun MessageReceivedEvent.punishmentReply(
        action: String,
        target: User,
        executor: Member,
        reason: String,
        dmSent: Boolean
    ) {
        val modChannel = guild.getTextChannelById(BOT.config.moderationChannelId) ?: channel

        reply("$CHECK_MARK $action ${target.asMention} ${if (dmSent) "with" else "without"} dm!")
        modChannel.embedSend(createModerationEmbed(action, target, executor, reason))
    }

    private fun createModerationEmbed(
        action: String,
        target: User,
        moderator: Member,
        reason: String,
    ): MessageEmbed {
        return EmbedBuilder()
            .setAuthor("${target.name} was $action", null, target.avatarUrl)
            .addField("Mention", target.asMention, false)
            .addField("Moderator", moderator.asMention, false)
            .addField("Reason", reason, false)
            .addField("Time", passedSince(SimpleTimeMark.now().toString()), false)
            .setColor(if (action == "unbanned") Color.GREEN else Color.RED)
            .build()
    }

    fun User.sendDM(action: String, reason: String, color: Color = Color.RED): Boolean {
        val dm = openPrivateChannel().complete()
        val embed = embed("You were $action!", "**Reason:** $reason", color)

        try {
            dm.sendMessageEmbeds(embed).complete()
        } catch (t: ErrorResponseException) {
            if (t.errorResponse == ErrorResponse.CANNOT_SEND_TO_USER) return false
        }

        return true
    }
}

@Suppress("unused")
class KickCommand : BaseCommand() {
    override val name: String = "kick"
    override val description: String = "Kicks a member from the server."
    override val options: List<Option> = listOf(
        Option("user", "The @ or id of the member you want to kick."),
        Option("reason", "The reason why this member is being kicked.")
    )
    override val async = true

    override fun MessageReceivedEvent.execute(args: List<String>) {
        val executor = member ?: error("Member is null.")

        val targetId = args[0].getId(guild) ?: return userError("Invalid user! Did you enter the correct @/id/name?")
        val reason = args.drop(1).joinToString(" ")

        val target = guild.retrieveMemberById(targetId).complete()

        val dmSent = target.user.sendDM("kicked", reason)

        target.kick().reason(reason).complete()
        punishmentReply("kicked", target.user, executor, reason, dmSent)
    }
}

@Suppress("unused")
class BanCommand : BaseCommand() {
    override val name: String = "ban"
    override val description: String = "Bans a member from the server."
    override val options: List<Option> = listOf(
        Option("user", "The @ or id of the member you want to ban."),
        Option("timeframe", "The timeframe (in days, max 7) where messages should be deleted in."),
        Option("reason", "The reason why this member is being banned.")
    )
    override val async = true

    override fun MessageReceivedEvent.execute(args: List<String>) {
        val executor = member ?: error("Member is null.")

        val targetId = args[0].getId(guild) ?: return userError("Invalid user! Did you enter the correct @/id/name?")
        val timeframe = args[1].toIntOrNull().takeIf { it != null && it <= 7 }
            ?: return userError("Invalid timeframe! Did you enter a number <= 7? !ban <user> <timeframe> <reason>")
        val reason = args.drop(2).joinToString(" ")

        val target = guild.retrieveMemberById(targetId).complete()

        val dmSent = target.user.sendDM("banned", reason)

        target.ban(timeframe, TimeUnit.DAYS).reason(reason).complete()
        punishmentReply("banned", target.user, executor, reason, dmSent)
    }
}

@Suppress("unused")
class UnbanCommand : BaseCommand() {
    override val name: String = "unban"
    override val description: String = "Unbans a member from the server."
    override val options: List<Option> = listOf(
        Option("user", "The @ or id of the member you want to unban."),
        Option("reason", "The reason why this member is being unbanned.")
    )
    override val async = true

    override fun MessageReceivedEvent.execute(args: List<String>) {
        val executor = member ?: error("Member is null.")

        val targetId = args[0].getId(guild) ?: return userError("Invalid user! Did you enter the correct @/id/name?")
        val reason = args.drop(1).joinToString(" ")

        val target = jda.retrieveUserById(targetId).complete()

        val dmSent = target.sendDM("unbanned", reason)

        guild.unban(target).reason(reason).complete()
        punishmentReply("unbanned", target, executor, reason, dmSent)
    }
}