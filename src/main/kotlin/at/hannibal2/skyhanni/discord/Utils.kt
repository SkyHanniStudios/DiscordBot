package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.slf4j.LoggerFactory

@Suppress("MemberVisibilityCanBePrivate")
object Utils {

    private val logger = LoggerFactory.getLogger(DiscordBot::class.java)

    fun MessageReceivedEvent.reply(text: String) {
        message.messageReply(text)
    }

    fun MessageReceivedEvent.replyWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        reply(text)
    }

    fun Message.messageDelete() {
        delete().queue()
    }

    fun Message.messageDeleteAndThen(consumer: () -> Unit) {
        delete().queue {
            consumer()
        }
    }

    fun Message.messageReply(text: String) {
        reply(text).queue()
    }

    fun MessageChannel.messageSend(text: String) {
        sendMessage(text).queue()
    }

    fun Message.replyWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        messageReply(text)
    }

    fun MessageChannel.sendMessageWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        messageSend(text)
    }

    fun MessageReceivedEvent.logAction(action: String) {
        val author = author
        val name = author.name
        val effectiveName = author.effectiveName
        val globalName = author.globalName
        val id = author.id
        logger.info("$effectiveName ($name/$globalName/$id) $action")
    }
}