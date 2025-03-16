package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BotMessageHandler
import at.hannibal2.skyhanni.discord.Utils.messageReply
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

abstract class CommandEvent {

    abstract val member: Member?

    abstract val author: User

    abstract val channel: MessageChannel

    abstract val message: Message?

    abstract val isFromGuild: Boolean

    abstract fun reply(text: String, ephemeral: Boolean = false)

    abstract fun reply(embed: MessageEmbed, ephemeral: Boolean = false)

    abstract fun userError(text: String)

    abstract fun sendError(text: String)

    fun replyWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        reply(text)
    }

    fun <T> doWhen(
        isMessage: (MessageReceivedEvent) -> T?,
        isSlashCommand: (SlashCommandInteractionEvent) -> T?
    ): T? {
        return when (this) {
            is MessageEvent -> isMessage(this.event)
            is SlashCommandEvent -> isSlashCommand(this.event)
            else -> null
        }
    }
}

class MessageEvent(val event: MessageReceivedEvent) : CommandEvent() {
    override val member: Member?
        get() = event.member
    override val author: User
        get() = event.author
    override val channel: MessageChannel
        get() = event.channel
    override val message: Message
        get() = event.message
    override val isFromGuild: Boolean
        get() = event.isFromGuild

    override fun reply(text: String, ephemeral: Boolean) {
        event.message.messageReply(text)
    }

    override fun reply(embed: MessageEmbed, ephemeral: Boolean) {
        event.message.messageReply(embed)
    }

    override fun userError(text: String) {
        event.message.messageReply("❌ $text")
    }

    override fun sendError(text: String) {
        event.message.messageReply("❌ An error occurred: $text")
    }
}

class SlashCommandEvent(val event: SlashCommandInteractionEvent) : CommandEvent() {
    override val member: Member?
        get() = event.member
    override val author: User
        get() = event.user
    override val channel: MessageChannel
        get() = event.channel
    override val message: Message?
        get() = null
    override val isFromGuild: Boolean
        get() = event.isFromGuild

    fun reply(text: String) {
        reply(text, false)
    }

    fun reply(embed: MessageEmbed) {
        reply(embed, false)
    }

    override fun reply(text: String, ephemeral: Boolean) {
        event.reply(text).setEphemeral(ephemeral).queue()
    }

    override fun reply(embed: MessageEmbed, ephemeral: Boolean) {
        event.replyEmbeds(embed).setEphemeral(ephemeral).queue()
    }

    override fun userError(text: String) {
        reply("❌ $text", ephemeral = true)
    }

    override fun sendError(text: String) {
        reply("❌ An error occurred: $text", ephemeral = true)
    }
}