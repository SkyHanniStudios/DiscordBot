package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object Utils {

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
}