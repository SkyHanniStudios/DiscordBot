package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.messageSend
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object LinkListener {

    private val githubPattern =
        "\\[SkyHanniStudios/DiscordBot] (New comment on pull request|Pull request review submitted:) #(?<pr>\\d+):? .+".toPattern()

    fun onMessage(bot: DiscordBot, event: MessageReceivedEvent) {
        event.onMessage(bot)
    }

    private fun MessageReceivedEvent.onMessage(bot: DiscordBot) {
        val embed = this.message.embeds[0]
        val title = embed.title ?: return

        val prNumber = getPr(title)?.toInt() ?: return
        val channelId = Database.getChannelId(prNumber) ?: return

        val guild = bot.jda.getGuildById(BOT.config.allowedServerId) ?: return
        val channel = guild.getThreadChannelById(channelId) ?: return

        channel.messageSend(this.message.embeds[0])
    }

    private fun getPr(title: String): String? {
        val matcher = githubPattern.matcher(title)
        if (!matcher.matches()) return null
        return matcher.group("pr")
    }
}