package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import java.awt.Color
import java.util.concurrent.TimeUnit

object ScamListener {

    fun onMessage(bot: DiscordBot, event: MessageReceivedEvent) {
        event.onMessage(bot)
    }

    private fun MessageReceivedEvent.onMessage(bot: DiscordBot) {
        if (channel.id != bot.config.scamDetectorChannelId) return
        if (this.author.isBot) return
        val member = member ?: return
        if (member.roles.maxBy { it.position } > bot.jda.roles.maxBy { it.position }) return

        var messageSent = false

        member.user.openPrivateChannel().queue { channel ->
            val messageEmbed =
                EmbedBuilder()
                    .setTitle("You've been banned!")
                    .setDescription("You've been banned from the SkyHanni Discord for posting a malicious message.")
                    .setColor(Color.RED)
                    .build()

            channel.sendMessageEmbeds(messageEmbed).queue(
                { messageSent = true },
                { error -> if (error is ErrorResponseException && error.errorCode == 52078) return@queue }
            )
        }

        member.ban(1, TimeUnit.DAYS).reason("Posting a malicious message").queueAfter(3, TimeUnit.SECONDS)

        Utils.sendMessageToBotChannel("Banned ${member.user.effectiveName} for posting a malicious message. DM ${if (messageSent) "sent" else "not possible"}.")
    }
}