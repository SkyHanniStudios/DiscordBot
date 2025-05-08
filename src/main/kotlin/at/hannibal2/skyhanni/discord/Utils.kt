package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.FileUpload
import java.awt.Color
import java.awt.Toolkit.getDefaultToolkit
import java.io.File
import java.util.zip.ZipFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

@Suppress("MemberVisibilityCanBePrivate", "TooManyFunctions")
object Utils {

    private inline val logger get() = BOT.logger

    fun MessageReceivedEvent.reply(text: String) {
        message.messageReply(text)
    }

    fun MessageReceivedEvent.userError(text: String) {
        message.messageReply("❌ $text")
    }

    fun MessageReceivedEvent.sendError(text: String) {
        message.messageReply("❌ An error occurred: $text")
        logAction("Error: $text")
    }

    fun MessageReceivedEvent.reply(embed: MessageEmbed) {
        message.messageReply(embed)
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

    fun Message.messageReply(embed: MessageEmbed) {
        replyEmbeds(embed).queue()
    }

    fun MessageChannel.messageSend(text: String, instantly: Boolean = false) {
        if (instantly) {
            sendMessage(text).complete()
        } else {
            sendMessage(text).queue()
        }
    }

    fun Message.replyWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        messageReply(text)
    }

    fun MessageChannel.sendMessageWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        messageSend(text)
    }

    fun sendMessageToBotChannel(text: String, instantly: Boolean = false) {
        BOT.jda.getTextChannelById(BOT.config.botCommandChannelId)?.messageSend(text, instantly)
    }

    fun MessageReceivedEvent.logAction(action: String, raw: Boolean = false) {
        if (raw) {
            logger.info(action)
            return
        }
        val name = author.name
        val id = author.id

        val nick = member?.nickname?.takeIf { it != "null" }
        val nickString = nick?.let { " (`$nick`)" } ?: ""

        val channelSuffix = if (isFromGuild) {
            val channelName = channel.name
            " in channel '$channelName'"
        } else ""
        logger.info("$id/$name$nickString $action$channelSuffix")
    }

    fun MessageReceivedEvent.hasAdminPermissions(): Boolean {
        val member = member ?: return false
        val allowedRoleIds = BOT.config.editPermissionRoleIds.values
        return !member.roles.none { it.id in allowedRoleIds }
    }

    fun MessageReceivedEvent.inBotCommandChannel() = channel.id == BOT.config.botCommandChannelId

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
        val days = inWholeDays
        val hours = inWholeHours % 24
        val minutes = inWholeMinutes % 60
        val seconds = inWholeSeconds % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}s")
        return parts.joinToString(" ")
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

    // keep comments as docs
    fun embed(title: String, body: String, color: Color, url: String? = null): MessageEmbed {
        val eb = EmbedBuilder()

        /*
    Set the title:
    1. Arg: title as string
    2. Arg: URL as string or could also be null
 */
        eb.setTitle(title, url)

        /*
    Set the color
 */
        eb.setColor(color)
//        eb.setColor(Color(0xF40C0C))
//        eb.setColor(Color(255, 0, 54))

        /*
    Set the text of the Embed:
    Arg: text as string
 */
        eb.setDescription(body)

        /*
    Add fields to embed:
    1. Arg: title as string
    2. Arg: text as string
    3. Arg: inline mode true / false
 */
//        eb.addField("Title of field", "test of field", false)

        /*
    Add spacer like field
    Arg: inline mode true / false
 */
//        eb.addBlankField(false)

        /*
    Add embed author:
    1. Arg: name as string
    2. Arg: url as string (can be null)
    3. Arg: icon url as string (can be null)
 */
//        eb.setAuthor(
//            "name", null, "https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/zekroBot_Logo_-_round_small.png"
//        )

        /*
    Set footer:
    1. Arg: text as string
    2. icon url as string (can be null)
 */
//        eb.setFooter(
//            "Text", "https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/zekroBot_Logo_-_round_small.png"
//        )

        /*
    Set image:
    Arg: image url as string
 */
//        eb.setImage("https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/logo%20-%20title.png")

        /*
    Set thumbnail image:
    Arg: image url as string
 */
//        eb.setThumbnail("https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/logo%20-%20title.png")

        return eb.build()
    }

    fun readStringFromClipboard(): String? = runCatching {
        getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
    }.getOrNull()

    fun User.getLinkName(): String = "<@$id>"

    fun Message.getLink(): String {
        val messageId = id
        val guildId = guild.id
        val channelId = channel.id

        return "https://discord.com/channels/$guildId/$channelId/$messageId"
    }
}