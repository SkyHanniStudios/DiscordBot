package at.hannibal2.skyhanni.discord.command


import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.BIG_X
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.SimpleTimeMark
import at.hannibal2.skyhanni.discord.Utils.embed
import at.hannibal2.skyhanni.discord.Utils.embedSend
import at.hannibal2.skyhanni.discord.Utils.getId
import at.hannibal2.skyhanni.discord.Utils.messageSend
import at.hannibal2.skyhanni.discord.Utils.passedSince
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.userError
import at.hannibal2.skyhanni.discord.command.ManageCommands.createModerationEmbed
import at.hannibal2.skyhanni.discord.command.ManageCommands.handleError
import at.hannibal2.skyhanni.discord.command.ManageCommands.sendDM
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import java.awt.Color
import java.util.concurrent.TimeUnit

object ManageCommands {
    fun createModerationEmbed(
        action: String,
        target: User,
        moderator: Member,
        reason: String,
        color: Color = Color.RED
    ): MessageEmbed {
        return EmbedBuilder()
            .setAuthor("${target.name} was $action", null, target.avatarUrl)
            .addField("Mention", target.asMention, false)
            .addField("Moderator", moderator.asMention, false)
            .addField("Reason", reason, false)
            .addField("Time", passedSince(SimpleTimeMark.now().toString()), false)
            .setColor(color)
            .build()
    }

    fun handleError(error: Throwable): String {
        return when (error) {
            is ErrorResponseException -> {
                when (error.errorResponse) {
                    ErrorResponse.UNKNOWN_MEMBER ->
                        "User not in guild!"

                    ErrorResponse.UNKNOWN_USER ->
                        "User doesn't exist!"

                    ErrorResponse.CANNOT_SEND_TO_USER ->
                        "Couldn't DM user!"

                    else -> "Discord API error: ${error.errorResponse}"
                }
            }

            else -> "${error.message}"
        }
    }

    fun User.sendDM(channel: MessageChannel, action: String, reason: String, color: Color = Color.RED) {
        openPrivateChannel().queue { dm ->
            val embed = embed("You were $action!", "**Reason:** $reason", color)

            dm.sendMessageEmbeds(embed).queue(
                { channel.messageSend("Sent DM to ${name}.") },
                { error -> channel.messageSend(handleError(error)) }
            )
        }
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

    override fun MessageReceivedEvent.execute(args: List<String>) {
        val mod = member ?: return reply("Internal error getting moderator.")
        if (!mod.hasPermission(Permission.KICK_MEMBERS) && !mod.hasPermission(Permission.ADMINISTRATOR))
            return reply("No perms $PLEADING_FACE")

        args[0].getId(guild) { targetId ->
            if (targetId == null) return@getId userError("Invalid user! Did you enter the correct @/id/name?")
            val reason = args.drop(1).joinToString(" ")

            guild.retrieveMemberById(targetId).queue(
                { target ->
                    val modChannel = guild.getTextChannelById(BOT.config.moderationChannelId) ?: channel

                    target.user.sendDM(modChannel, "kicked", reason)

                    target.kick().reason(reason).queue {
                        modChannel.embedSend(createModerationEmbed("kicked", target.user, mod, reason))
                    }
                },
                { error -> reply("$BIG_X ${handleError(error)}") }
            )
        }
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

    override fun MessageReceivedEvent.execute(args: List<String>) {
        val mod = member ?: return reply("Internal error getting moderator.")
        if (!mod.hasPermission(Permission.BAN_MEMBERS) && !mod.hasPermission(Permission.ADMINISTRATOR))
            return reply("No perms $PLEADING_FACE")

        args[0].getId(guild) { targetId ->
            if (targetId == null) return@getId userError("Invalid user! Did you enter the correct @/id/name?")
            val timeframe = args[1].toIntOrNull().takeIf { it != null && it <= 7 }
                ?: return@getId userError("Invalid timeframe! Did you enter a number <= 7?")
            val reason = args.drop(2).joinToString(" ")

            guild.retrieveMemberById(targetId).queue(
                { target ->
                    val modChannel = guild.getTextChannelById(BOT.config.moderationChannelId) ?: channel

                    target.user.sendDM(modChannel, "banned", reason)

                    target.ban(timeframe, TimeUnit.DAYS).reason(reason).queue {
                        modChannel.embedSend(createModerationEmbed("banned", target.user, mod, reason))
                    }
                },
                { error -> reply("$BIG_X ${handleError(error)}") }
            )
        }
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

    override fun MessageReceivedEvent.execute(args: List<String>) {
        val mod = member ?: return reply("Internal error getting moderator.")
        if (!mod.hasPermission(Permission.BAN_MEMBERS) && !mod.hasPermission(Permission.ADMINISTRATOR))
            return reply("No perms $PLEADING_FACE")

        args[0].getId(guild) { targetId ->
            if (targetId == null) return@getId userError("Invalid user! Did you enter the correct @/id/name?")
            val reason = args.drop(1).joinToString(" ")

            jda.retrieveUserById(targetId).queue(
                { target ->
                    val modChannel = guild.getTextChannelById(BOT.config.moderationChannelId) ?: channel

                    target.sendDM(modChannel, "unbanned", reason)

                    guild.unban(target).reason(reason).queue {
                        modChannel.embedSend(
                            createModerationEmbed("unbanned", target, mod, reason, color = Color.GREEN)
                        )
                    }
                },
                { error -> reply("$BIG_X ${handleError(error)}") }
            )
        }
    }
}