package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.Utils.userError
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.awt.Color
import java.time.Instant
import java.time.temporal.ChronoUnit

// TODO: Either move to config.json and BotConfig.kt + Utils.kt or just change the IDs to real server's.
// Placeholder IDs for the test server
private const val COMMUNITY_HELPER_ROLE_ID = "1510199336007631002"
private const val JAILED_ROLE_ID           = "1510199262925819965"
private const val JAIL_LOG_CHANNEL_ID      = "1346269715697242115"

private fun MessageReceivedEvent.hasJailPermissions(): Boolean =
    member?.roles?.any { it.id == COMMUNITY_HELPER_ROLE_ID } ?: false

/**
 * Resolves a member from mentions and user Id.
 * Returns null and replies with an error if not uniquely identified.
 */
private fun MessageReceivedEvent.resolveTarget(query: String): Member? {
    val cleanId = query.replace(Regex("[<@!>]"), "")
    if (cleanId.isEmpty() || !cleanId.all { it.isDigit() }) {
        userError("Invalid user - provide a mention or userID.")
        return null
    }
    val member = guild.getMemberById(cleanId)
        ?: runCatching { guild.retrieveMemberById(cleanId).complete() }.getOrNull()
    if (member != null) return member
    userError("No member found with ID `$cleanId`.")
    return null
}

private fun MessageReceivedEvent.executeJail(args: List<String>, commandName: String, purge: Boolean) {
    if (!hasJailPermissions()) {
        reply("No permissions $PLEADING_FACE")
        return
    }

    if (args.size < 2) return userError("<user> <reason>")

    val query = args[0]
    val reason = args.drop(1).joinToString(" ")

    val targetMember = resolveTarget(query) ?: return

    if (targetMember.user.isBot) {
        userError("You cannot jail a bot.")
        return
    }

    val staffRoleIds = BOT.config.editPermissionRoleIds.values
    if (targetMember.roles.any { it.id in staffRoleIds }) {
        userError("You cannot jail a staff member.")
        return
    }

    if (targetMember.roles.any { it.id == JAILED_ROLE_ID }) {
        userError("${targetMember.effectiveName} is already jailed.")
        return
    }

    val jailedRole = guild.getRoleById(JAILED_ROLE_ID) ?: run {
        userError("Jailed role not found in this server.")
        return
    }

    val executor = member!!
    logAction("used $commandName on ${targetMember.user.name} (${targetMember.id}) — reason: $reason")

    guild.addRoleToMember(targetMember, jailedRole).queue(
        {},
        { error -> userError("Failed to add Jailed role: ${error.message}") }
    )

    val eventMessage = message
    val targetId = targetMember.id
    val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
    val textChannel = channel.asTextChannel()

    Utils.runAsync("jail-$targetId") {
        val purgedCount: Int

        if (purge) {
            // Purge up to 30 of the target's messages from the last hour in this channel
            val toDelete = mutableListOf<Message>()
            var lastId = eventMessage.id
            var keepFetching = true

            while (keepFetching) {
                val batch = textChannel.getHistoryBefore(lastId, 100).complete().retrievedHistory
                if (batch.isEmpty()) break

                for (msg in batch) {
                    if (msg.timeCreated.toInstant().isBefore(oneHourAgo)) {
                        keepFetching = false
                        break
                    }
                    if (msg.author.id == targetId) toDelete.add(msg)
                    if (toDelete.size >= 30) {
                        keepFetching = false
                        break
                    }
                }

                lastId = batch.last().id
            }

            if (toDelete.isNotEmpty()) {
                toDelete.chunked(100).forEach { chunk ->
                    if (chunk.size == 1) chunk.first().delete().complete()
                    else textChannel.deleteMessages(chunk).complete()
                }
            }

            purgedCount = toDelete.size
        } else {
            purgedCount = 0
        }

        val purgeMessage = if (purgedCount > 0) " · $purgedCount message(s) purged" else ""
        eventMessage.reply("🔒 Jailed ${targetMember.asMention}$purgeMessage. Reason: *$reason*").queue()

        // Log to admin channel
        BOT.jda.getTextChannelById(JAIL_LOG_CHANNEL_ID)?.sendMessageEmbeds(
            EmbedBuilder()
                .setTitle("User Jailed 🔒")
                .setColor(Color(0xE74C3C))
                .addField("User", "${targetMember.asMention} `${targetMember.user.name}` (${targetMember.id})", false)
                .addField("Jailed By", "${executor.asMention} `${executor.user.name}` (${executor.id})", false)
                .addField("Reason", reason, false)
                .addField("Channel", textChannel.asMention, true)
                .addField("Messages Purged", if (purge) purgedCount.toString() else "N/A", true)
                .setTimestamp(Instant.now())
                .build()
        )?.queue()
    }
}

@Suppress("unused")
class JailCommand : BaseCommand() {
    override val name: String = "jail"
    override val description: String = "Jails a user: assigns the Jailed role and logs the action."
    override val options: List<Option> = listOf(
        Option("user", "User mention or exact userID."),
        Option("reason", "Reason for jailing.")
    )
    override val userCommand: Boolean = true

    override fun MessageReceivedEvent.execute(args: List<String>) = executeJail(args, "!jail", purge = false)
}

@Suppress("unused")
class DJailCommand : BaseCommand() {
    override val name: String = "djail"
    override val description: String = "Jails a user and purges up to 30 of their recent messages from this channel."
    override val options: List<Option> = listOf(
        Option("user", "User mention or exact userID."),
        Option("reason", "Reason for jailing.")
    )
    override val userCommand: Boolean = true

    override fun MessageReceivedEvent.execute(args: List<String>) = executeJail(args, "!djail", purge = true)
}