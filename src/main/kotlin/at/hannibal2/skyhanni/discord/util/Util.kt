package at.hannibal2.skyhanni.discord.util

import at.hannibal2.skyhanni.discord.ConfigLoader
import at.hannibal2.skyhanni.discord.util.EventUtil.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object Util {

    private val config = ConfigLoader.load("src/main/kotlin/at/hannibal2/skyhanni/discord/config.json")

    fun MessageReceivedEvent.hasPermissions(): Boolean {
        val member = this.member ?: return false
        val allowedRoleIds = config.editPermissionRoleIds.values
        if (!member.roles.any { it.id in allowedRoleIds }) {
            this.reply("No perms \uD83E\uDD7A")
            // User doesn't have an allowed role; you can ignore or send a warning.
            return false
        }

        return true
    }
}