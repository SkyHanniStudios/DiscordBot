package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstraction over the two ways a command can be invoked: as a prefix command inside a normal
 * message, or as a slash command interaction. Commands only ever see this type and do not know
 * which way was used.
 */
sealed class CommandEvent {

    abstract val author: User
    abstract val member: Member?
    abstract val channel: MessageChannelUnion
    abstract val guild: Guild

    /** The prefix used to invoke commands this way, for usage and help texts. */
    abstract val prefix: String

    /** The message that invoked the command. Null for slash commands, they have none. */
    abstract val message: Message?

    /** The message the invocation was a reply to. Null for slash commands. */
    abstract val referencedMessage: Message?

    abstract fun sendReply(text: String)

    /** [content] is sent as plain text above the embed. Mentions inside an embed never notify anyone. */
    abstract fun sendReply(embed: MessageEmbed, content: String? = null)

    abstract fun sendReplyWithConsumer(text: String, consumer: (Message) -> Unit)

    /**
     * Deletes the message that invoked the command and runs [then] afterwards.
     * Slash commands have no invocation message, so [then] runs right away.
     */
    abstract fun deleteInvocation(then: () -> Unit)
}

class MessageCommandEvent(val event: MessageReceivedEvent) : CommandEvent() {

    override val author: User get() = event.author
    override val member: Member? get() = event.member
    override val channel: MessageChannelUnion get() = event.channel
    override val guild: Guild get() = event.guild
    override val prefix: String get() = "!"
    override val message: Message get() = event.message
    override val referencedMessage: Message? get() = event.message.referencedMessage

    override fun sendReply(text: String) {
        event.message.reply(text).queue()
    }

    override fun sendReply(embed: MessageEmbed, content: String?) {
        event.message.replyEmbeds(embed).setContent(content).queue()
    }

    override fun sendReplyWithConsumer(text: String, consumer: (Message) -> Unit) {
        event.message.reply(text).queue { consumer(it) }
    }

    override fun deleteInvocation(then: () -> Unit) {
        event.message.delete().queue { then() }
    }
}

class SlashCommandEvent(val event: SlashCommandInteractionEvent) : CommandEvent() {

    // the first reply replaces the "thinking" placeholder created by deferReply,
    // every later one has to be a separate follow up message
    private val answered = AtomicBoolean(false)

    override val author: User get() = event.user
    override val member: Member? get() = event.member
    override val channel: MessageChannelUnion get() = event.channel
    override val guild: Guild get() = event.guild ?: error("slash command used outside of a guild")
    override val prefix: String get() = "/"
    override val message: Message? get() = null
    override val referencedMessage: Message? get() = null

    override fun sendReply(text: String) {
        if (answered.compareAndSet(false, true)) {
            event.hook.editOriginal(text).queue()
        } else {
            event.hook.sendMessage(text).queue()
        }
    }

    override fun sendReply(embed: MessageEmbed, content: String?) {
        if (answered.compareAndSet(false, true)) {
            event.hook.editOriginalEmbeds(embed).setContent(content).queue()
        } else {
            event.hook.sendMessageEmbeds(embed).setContent(content).queue()
        }
    }

    override fun sendReplyWithConsumer(text: String, consumer: (Message) -> Unit) {
        if (answered.compareAndSet(false, true)) {
            event.hook.editOriginal(text).queue { consumer(it) }
        } else {
            event.hook.sendMessage(text).queue { consumer(it) }
        }
    }

    override fun deleteInvocation(then: () -> Unit) {
        then()
    }
}
