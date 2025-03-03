package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.FileUpload
import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

@Suppress("MemberVisibilityCanBePrivate", "TooManyFunctions")
object Utils {

    private val logger = LoggerFactory.getLogger(DiscordBot::class.java)

    fun MessageReceivedEvent.reply(text: String) {

        println("replay: $text")
        message.messageReply(text)
    }

    fun MessageReceivedEvent.replyWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        reply(text)
    }

    fun Message.messageDelete() {
        delete().queue()
    }

    fun Message.messageDeleteAndThen(consumer: () -> Unit) {
        delete().queue {
            consumer()
        }
    }

    fun Message.messageReply(text: String) {
        reply(text).queue()
    }

    fun MessageChannel.messageSend(text: String) {
        sendMessage(text).queue()
    }

    fun Message.replyWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        messageReply(text)
    }

    fun MessageChannel.sendMessageWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        messageSend(text)
    }

    fun MessageReceivedEvent.logAction(action: String) {
        val author = author
        val name = author.name
        val effectiveName = author.effectiveName
        val globalName = author.globalName
        val id = author.id
        logger.info("$effectiveName ($name/$globalName/$id) $action")
    }

    fun runDelayed(duration: Duration, consumer: () -> Unit) {
        Thread {
            Thread.sleep(duration.inWholeMilliseconds)
            consumer()
        }.start()
    }

    fun unzipFile(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    inline fun <T> timeExecution(block: () -> T): Pair<T, Duration> {
        val start = System.nanoTime()
        val result = block()
        val duration = System.nanoTime() - start
        return result to duration.nanoseconds
    }

    fun Duration.format(): String {
        val s = inWholeSeconds
        val m = inWholeMinutes
        return "${m}m ${s}s"
    }

    fun File.createParentDirIfNotExist() {
        parentFile?.mkdirs()
    }

    fun MessageChannelUnion.uploadFile(jarFile: File, comment: String) {
        val textChannel = this as? TextChannel ?: error("not a text channel: $name")
        val fileUpload = FileUpload.fromData(jarFile, jarFile.name)
        textChannel.sendFiles(fileUpload).addContent(comment).queue()

    }

    fun String.linkTo(link: String): String = "[$this](<$link>)"
}