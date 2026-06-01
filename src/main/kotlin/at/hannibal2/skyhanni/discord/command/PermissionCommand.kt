package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.PARTY_FACE
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils.hasStaffRole
import at.hannibal2.skyhanni.discord.Utils.messageAuthor
import at.hannibal2.skyhanni.discord.Utils.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

@Suppress("unused")
class PermTestCommand : BaseCommand() {
    override val name: String = "testperms"
    override val description: String = "Test the permissions of yourself"
    override val permission = Permission.USER

    override fun MessageReceivedEvent.execute(args: List<String>) {
        for (role in StaffRole.entries) {
            if (messageAuthor.hasStaffRole(role)) {
                reply("You have the role ${role.roleName} $PARTY_FACE")
                return
            }
        }
        reply("You do not have any staff role $PLEADING_FACE")
    }
}

enum class Permission(val staffRole: StaffRole?) {
    ADMIN(StaffRole.ADMIN),
    MODERATOR(StaffRole.MODERATOR),
    COMMUNITY_HELPER(StaffRole.COMMUNITY_HELPER),
    USER(null),
    ;
}

enum class StaffRole(val roleName: String) {
    ADMIN("Admin"),
    MODERATOR("Moderator"),
    COMMUNITY_HELPER("Community Helper"),
    ;
}
