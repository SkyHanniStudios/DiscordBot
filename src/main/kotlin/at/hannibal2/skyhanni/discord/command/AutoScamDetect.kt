package at.hannibal2.skyhanni.discord.command

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.sourceforge.tess4j.Tesseract
import java.io.File
import java.util.concurrent.TimeUnit

object AutoScamDetect {
    fun ocrImageText(imageFile: File, lang: String = "eng"): String {
        val tesseract = Tesseract()
        tesseract.setLanguage(lang)
        return tesseract.doOCR(imageFile)
    }

    fun checkAndBan(event: MessageReceivedEvent) {
        val toCheck = event.message.attachments.filter { it.isImage }
        val anyScam = toCheck.any {
            val file = File.createTempFile("ocr_temp", ".png").apply {
                it.proxy.downloadToFile(this)
            }
            val extractedText = ocrImageText(file)
            val containedKeyWords = keyWords.filter { extractedText.contains(it, ignoreCase = true) }
            if (containedKeyWords.size >= 6) {
                true
            } else {
                false
            }
        }
        if (!anyScam) return
        event.guild.ban(event.author, 1, TimeUnit.HOURS).reason("Scam image detected").queue()
        event.channel.sendMessage("User ${event.author.asMention} has been automatically banned for sending a scam image.").queue()
    }

    val keyWords = setOf(
        "mrbeast",
        "beast",
        "$",
        "crypto",
        "casino",
        "promo",
        "reward",
        "withdraw",
        "claim",
        "celebrate",
    )
}