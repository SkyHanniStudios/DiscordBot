package at.hannibal2.skyhanni.discord

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.FileUpload
import java.awt.Color
import java.io.File
import java.util.zip.ZipFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

@Suppress("MemberVisibilityCanBePrivate", "TooManyFunctions")
object Utils {

    private inline val logger get() = BOT.logger

    fun reply(text: String, event: Any, ephemeral: Boolean = false) {
        doWhen(event, {
            it.message.messageReply(text)
        }, {
            it.reply(text).setEphemeral(ephemeral).queue()
        })
    }

    fun reply(embed: MessageEmbed, event: Any, ephemeral: Boolean = false) {
        doWhen(event, {
            it.message.messageReply(embed)
        }, {
            it.replyEmbeds(embed).setEphemeral(ephemeral).queue()
        })
    }

    fun userError(text: String, event: Any, ephemeral: Boolean = true) {
        doWhen(event, {
            it.message.messageReply("❌ $text")
        }, {
            it.reply("❌ $text").setEphemeral(ephemeral).queue()
        })
    }

    fun sendError(text: String, event: Any, ephemeral: Boolean = true) {
        doWhen(event, {
            it.message.messageReply("❌ $text")
        }, {
            it.reply("❌ $text").setEphemeral(ephemeral).queue()
        })

        logAction("Error: $text", event)
    }

    fun MessageReceivedEvent.replyWithConsumer(text: String, consumer: (MessageReceivedEvent) -> Unit) {
        BotMessageHandler.log(text, consumer)
        reply(text, this)
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

    fun logAction(action: String, event: Any, raw: Boolean = false) {
        val author = when (event) {
            is MessageReceivedEvent -> event.author
            is SlashCommandInteractionEvent -> event.user
            else -> throw IllegalArgumentException("Unknown event type")
        }

        val member = when (event) {
            is MessageReceivedEvent -> event.member
            is SlashCommandInteractionEvent -> event.member
            else -> throw IllegalArgumentException("Unknown event type")
        }

        val channel = when (event) {
            is MessageReceivedEvent -> event.channel
            is SlashCommandInteractionEvent -> event.channel
            else -> throw IllegalArgumentException("Unknown event type")
        }

        if (raw) {
            logger.info(action)
            return
        }

        val name = author.name
        val id = author.id
        val nickString = member?.nickname?.takeIf { it != "null" }?.let { " (`$it`)" } ?: ""
        val isFromGuild =
            (event as? MessageReceivedEvent)?.isFromGuild ?: (event as? SlashCommandInteractionEvent)?.isFromGuild
            ?: false
        val channelSuffix = if (isFromGuild) " in channel '${channel.name}'" else ""

        logger.info("$id/$name$nickString $action$channelSuffix")
    }

    fun hasAdminPermissions(event: Any): Boolean {
        val member = doWhen(
            event,
            { it.member },
            { it.member }
        ) as Member

        val allowedRoleIds = BOT.config.editPermissionRoleIds.values
        return !member.roles.none { it.id in allowedRoleIds }
    }

    fun <T> doWhen(
        event: Any,
        consumer: (MessageReceivedEvent) -> T?,
        consumer2: (SlashCommandInteractionEvent) -> T?
    ): T? {
        return when (event) {
            is MessageReceivedEvent -> consumer(event)
            is SlashCommandInteractionEvent -> consumer2(event)
            else -> null
        }
    }

    fun inBotCommandChannel(event: Any): Boolean {
        val id = doWhen(event, { it.channel.id }, { it.channel.id })
        return id == BOT.config.botCommandChannelId
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
    fun embed(title: String, body: String, color: Color): MessageEmbed {
        val eb = EmbedBuilder()

        /*
    Set the title:
    1. Arg: title as string
    2. Arg: URL as string or could also be null
 */
        eb.setTitle(title, null)

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
}