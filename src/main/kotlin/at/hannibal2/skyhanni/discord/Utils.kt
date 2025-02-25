package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object Utils {

    fun MessageReceivedEvent.reply(text: String) {
        message.reply(text).queue()
    }
}