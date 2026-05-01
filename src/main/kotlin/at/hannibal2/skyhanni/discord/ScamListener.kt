package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.messageDelete
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import java.awt.Color


object ScamListener {

    val purgatory by lazy { BOT.jda.getTextChannelById(BOT.config.scamPurgatoryChannelId) }
    val memberRole by lazy { BOT.jda.getGuildById(BOT.config.allowedServerId)?.getRoleById(BOT.config.memberRoleId) }

    fun onMessage(bot: DiscordBot, event: MessageReceivedEvent) {
        event.onMessage(bot)
    }

    private fun MessageReceivedEvent.onMessage(bot: DiscordBot) {
        if (channel.id != bot.config.scamDetectorChannelId) return
        if (this.author.isBot) return
        val member = member ?: return
        val user = member.user
        if (!guild.selfMember.canInteract(member)) return
        val purgatory = purgatory ?: return
        val guild = message.guild
        val name = user.effectiveName

        guild.modifyMemberRoles(member, listOf(), listOf(memberRole)).queue()

        val embed = EmbedBuilder()
            .setAuthor(name, user.avatarUrl ?: "https://cdn.discordapp.com/embed/avatars/0.png?size=512") // default avatar
            .addField("Display name", member.nickname ?: name, true)
            .addField("ID", user.id, true)
            .addField("Mention", user.asMention, true)
            .addField("Member since", "<t:${member.timeJoined.toEpochSecond()}:F>", true)
            .addField("Account created", "<t:${user.timeCreated.toEpochSecond()}:F>", true)
            .setColor(Color.YELLOW)
            .build()

        purgatory.sendMessageEmbeds(embed).queue()
        message.forwardTo(purgatory).queue()
        deleteRecentMessages(guild, member)
        purgatory.upsertPermissionOverride(member)
            .grant(Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL).queue()

        var messageSent = false

        user.openPrivateChannel().queue { channel ->
            val messageEmbed =
                EmbedBuilder()
                    .setTitle("You've been muted!")
                    .setDescription("You've been muted from the SkyHanni Discord for posting a malicious message.")
                    .setColor(Color.RED)
                    .build()

            channel.sendMessageEmbeds(messageEmbed).queue(
                { messageSent = true },
                { error -> if (error is ErrorResponseException && error.errorCode == 52078) return@queue }
            )
        }

        Utils.sendMessageToBotChannel("Muted $name for posting a malicious message. DM ${if (messageSent) "sent" else "not possible"}.")
    }

    fun deleteRecentMessages(guild: Guild, member: Member) {
        guild.textChannels.forEach { channel ->
            if (!channel.canTalk(member)) return@forEach
            channel.iterableHistory
                .takeAsync(20)
                .thenAccept { messages ->
                    messages
                        .filter { it.author.id == member.user.id }
                        .forEach { msg ->
                            msg.messageDelete()
                        }
                }
        }
    }
}