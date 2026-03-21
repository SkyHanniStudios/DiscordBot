package at.hannibal2.skyhanni.discord.command

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.internal.entities.emoji.CustomEmojiImpl

object PleadReactor {

    fun doPleadReact(event: MessageReceivedEvent, message: String) {
        if (!message.contains(":pleading_face:") && !message.contains("\uD83E\uDD7A")) return
        event.message.addReaction(CustomEmojiImpl("pleading_face", 1484796321901838467, false)).queue()
    }
}