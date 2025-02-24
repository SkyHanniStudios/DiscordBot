package at.hannibal2.skyhanni.discord.util

import at.hannibal2.skyhanni.discord.CommandOption
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object EventUtil {

    fun MessageReceivedEvent.missingArguments(args: List<String>, possible: List<CommandOption>): Boolean {
        val missing = possible.filter { it.required && args.size <= possible.indexOf(it) }.map { it.name }

        if (missing.isNotEmpty()) {
            this.reply("❌ Missing arguments: ${missing.joinToString(", ")}")
            return true
        }

        return false
    }

    fun MessageReceivedEvent.logAction(action: String) {
        val author = this.author
        val name = author.name
        val effectiveName = author.effectiveName
        val globalName = author.globalName
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        println("$time $effectiveName ($name/$globalName) $action")
    }

    fun MessageReceivedEvent.reply(message: String? = null, data: MessageCreateData? = null) {
        when {
            data != null -> this.message.reply(data).queue()
            message != null -> this.message.reply(message).queue()
        }
    }
}