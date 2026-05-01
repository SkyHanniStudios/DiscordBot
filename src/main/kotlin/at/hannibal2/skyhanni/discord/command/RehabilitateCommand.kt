package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.Option
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.sendError
import at.hannibal2.skyhanni.discord.Utils.userError
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object RehabilitateCommand : BaseCommand() {
    override val name: String = "rehabilitate"
    override val description: String = "Help a member re-enter society."
    override val options: List<Option> = listOf(
        Option("member", "Mention or ID of the member"),
    )

    override val userCommand: Boolean = false

    val purgatory by lazy { BOT.jda.getTextChannelById(BOT.config.scamPurgatoryChannelId) }
    val memberRole by lazy { BOT.jda.getGuildById(BOT.config.allowedServerId)?.getRoleById(BOT.config.memberRoleId) }

    private val mentionPattern = "<@(?<id>\\d+)>".toPattern()

    override fun MessageReceivedEvent.execute(args: List<String>) {
        val purgatory = purgatory ?: return
        val memberRole = memberRole ?: return
        val memberArg = args.firstOrNull() ?: return userError("Usage: !rehabilitate <id/mention>")

        val userId = memberArg.replace(Regex("<@!?(\\d+)>"), "$1").toLongOrNull()
            ?: return userError("That's not a valid member! Example: 429289516021448705 or a mention.")

        try {
            guild.retrieveMemberById(userId).queue({ member ->
                if (!guild.selfMember.canInteract(member)) return@queue sendError("I can't manage that member!")
                if (member.roles.contains(memberRole)) return@queue userError("That member already is a functioning member of society!")

                purgatory.manager.removePermissionOverride(member).queue()
                guild.addRoleToMember(member, memberRole).queue()

                Utils.sendMessageToBotChannel("Successfully rehabilitated ${member.asMention}!")
            }, {
                return@queue sendError("Couldn't load member.")
            })
        } catch (e: NumberFormatException) {
            userError("Not a valid ID! Example: 429289516021448705 or a mention.")
            return
        }
    }
}