package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.messageSend
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object LinkListener {

    private const val GITHUB_WEBHOOK_ID = "1347997547368550461"
    private val githubPattern =
        "\\[ILike2WatchMemes/DiscordBot] (New comment on pull request|Pull request review submitted:) #(?<pr>\\d+):? .+".toPattern()

    fun onMessage(bot: DiscordBot, event: MessageReceivedEvent) {
        event.onMessage(bot)
    }

    private fun MessageReceivedEvent.onMessage(bot: DiscordBot) {
        if (this.author.id == GITHUB_WEBHOOK_ID) {
            val embed = this.message.embeds[0]

            val title = embed.title ?: return

            val prNumber = getPr(title)?.toInt() ?: return
            val link = Database.getLink(prNumber) ?: return

            val guild = bot.jda.getGuildById(BOT.config.allowedServerId) ?: return
            val channel = guild.getThreadChannelById(link.channel) ?: return


            channel.messageSend(this.message.embeds[0])
        }
    }

    private fun getPr(title: String): String? {
        val matcher = githubPattern.matcher(title)
        if (!matcher.matches()) return null
        return matcher.group("pr")
    }
}