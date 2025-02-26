package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object BotMessageHandler {
    private val loggedMessages = mutableMapOf<String, MutableList<(MessageReceivedEvent) -> Unit>>()

    fun handle(event: MessageReceivedEvent) {
        loggedMessages.remove(event.message.contentRaw)?.forEach {
            it.invoke(event)
        }
    }

    fun log(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        loggedMessages.getOrPut(text) { mutableListOf() }.add(consumer)
    }
}