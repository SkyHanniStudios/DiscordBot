package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BotConfig
import at.hannibal2.skyhanni.discord.ConfigLoader
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.sourceforge.tess4j.Tesseract
import java.awt.Color
import java.io.File

object AutoScamDetect {
    fun ocrImageText(imageFile: File, lang: String = "eng"): String {
        val tesseract = Tesseract()
        tesseract.setLanguage(lang)
        return tesseract.doOCR(imageFile)
    }

    fun checkAndBan(event: MessageReceivedEvent, config: BotConfig) {
        val toCheck = event.message.attachments.filter { it.isImage }
        val detectedScamWordOrNull = toCheck.firstNotNullOfOrNull { imageAttachments ->
            val file = File.createTempFile("ocr_temp", ".png").apply {
                imageAttachments.proxy.downloadToFile(this)
            }
            val extractedText = ocrImageText(file)
            file.delete()
            return@firstNotNullOfOrNull config.scamKeywordConfig.firstNotNullOfOrNull { it.textTriggersKeywords(extractedText) }
        } ?: return
        try {
            event.message.delete().reason("Detected Scam. $detectedScamWordOrNull").complete()
            val embedBuilder = EmbedBuilder()
            embedBuilder.setColor(Color.RED)
            embedBuilder.setTitle("SkyHanni Server Kick")
            embedBuilder.setDescription("Our Scam Detection flagged you for a Scam Message. You were kicked from SH due to this!")
            event.member?.user?.openPrivateChannel()?.complete()?.sendMessageEmbeds(
                embedBuilder.build()
            )?.complete()
        } catch (_: Throwable) {
            //might have send another message before and got kicked already...
        }

        try {
            event.guild.kick(event.author).reason("Scam image detected: $detectedScamWordOrNull").queue()
        }catch (_: Throwable){
            //User might has been kicked already
        }
        event.channel.sendMessage("User ${event.author.asMention} has been automatically banned for sending a scam image.").queue()
    }


    fun ConfigLoader.ScamKeywordConfig.textTriggersKeywords(text: String): String? {
        val containedKeyWords = keyWords.filter { word -> text.contains(word, ignoreCase = true) }.toList()
        if ((containedKeyWords.size >= requiredFindings)) {
            return containedKeyWords.joinToString(", ")
        } else {
            return null
        }
    }
}
