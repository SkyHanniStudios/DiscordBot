package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BotConfig
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
        var foundScamWords: String? = null
        val anyScam = toCheck.any {
            val file = File.createTempFile("ocr_temp", ".png").apply {
                it.proxy.downloadToFile(this)
            }
            val extractedText = ocrImageText(file)
            return@any config.scamKeywordConfig.any {
                val containedKeyWords = it.keyWords.filter { extractedText.contains(it, ignoreCase = true) }.toList()
                return@any if ((containedKeyWords.size >= it.requiredFindings)) {
                    foundScamWords = containedKeyWords.joinToString(", ")
                    true
                } else {
                    false
                }
            }
        }
        if (!anyScam) return
        try {
            event.message.delete().reason("Detected Scam. $foundScamWords").complete()
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
            event.guild.kick(event.author).reason("Scam image detected: $foundScamWords").queue()
        }catch (_: Throwable){
            //User might has been kicked already
        }
        event.channel.sendMessage("User ${event.author.asMention} has been automatically banned for sending a scam image.").queue()
    }
}