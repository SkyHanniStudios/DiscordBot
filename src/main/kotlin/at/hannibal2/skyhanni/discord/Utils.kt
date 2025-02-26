package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.slf4j.LoggerFactory

object Utils {

    private val logger = LoggerFactory.getLogger(DiscordBot::class.java)

    fun MessageReceivedEvent.reply(text: String) {
        message.reply(text).queue()
    }

    fun MessageReceivedEvent.replyWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        reply(text)
    }

    fun Message.replyWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        reply(text).queue()
    }

    fun MessageChannel.sendMessageWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        sendMessage(text).queue()
    }

    fun MessageReceivedEvent.logAction(action: String) {
        val author = author
        val name = author.name
        val effectiveName = author.effectiveName
        val globalName = author.globalName
        logger.info("$effectiveName ($name/$globalName) $action")
    }
}