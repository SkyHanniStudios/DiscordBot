package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.internal.entities.emoji.CustomEmojiImpl
import kotlin.random.Random

object PleadReactor {

    val customPlead = CustomEmojiImpl("pleading_face", 1484796321901838467, false)

    fun doPleadReact(event: MessageReceivedEvent, message: String) {
        if (!message.contains(":pleading_face:") && !message.contains("\uD83E\uDD7A")) return
        val random = Random.nextInt(100)
        if (random == 0) {
            event.reply(PLEADING_FACE)
        } else if (random < 7) {
            event.reply(customPlead.asMention)
        } else if (random < 25) {
            event.message.addReaction(customPlead).queue()
        }
    }
}