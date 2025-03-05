package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.logAction
import at.hannibal2.skyhanni.discord.Utils.replyT
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

@Suppress("UNUSED_PARAMETER")
class ServerSlashCommands(private val config: BotConfig, commands: SlashCommandListener) {
    init {
        commands.add(SlashCommand("server", userCommand = true) { event -> event.serverCommand() })
        commands.add(SlashCommand("serverlist") { event -> event.serverList() })
        commands.add(SlashCommand("serveradd") { event -> event.serverAdd() })
        commands.add(SlashCommand("serveredit") { event -> event.serverEdit() })
        commands.add(SlashCommand("serveraddalias") { event -> event.serverAddAlias() })
        commands.add(SlashCommand("serveraliasdelete") { event -> event.serverAliasDelete() })
        commands.add(SlashCommand("serverdelete") { event -> event.serverDelete() })
    }

    private fun SlashCommandInteractionEvent.serverCommand() {
        val keyword = getOption("keyword")?.asString ?: return
        val debug = getOption("debug")?.asBoolean ?: false

        val server = Database.getServer(keyword)
        if (server != null) {
            if (debug) {
                replyT(server.printDebug())
            } else {
                replyT(server.print())
            }
        } else {
            replyT("Server with keyword '$keyword' not found.", ephemeral = true)
        }
    }

    private fun SlashCommandInteractionEvent.serverAdd() {
        val keyword = getOption("keyword")?.asString ?: return

        if (Database.getServer(keyword) != null) {
            replyT("❌ Server already exists. Use `!serveredit` instead.")
            return
        }

        val server = createServer(keyword) ?: return
        if (Database.addServer(server)) {
            val id = member?.id
            replyT("✅ Server '$keyword' added by <@$id>:")
            channel.sendMessage(server.print()).queue()
            logAction("added server '$keyword'")
        } else {
            replyT("❌ Failed to add server.", ephemeral = true)
        }
    }

    private fun SlashCommandInteractionEvent.serverEdit() {
        val keyword = getOption("keyword")?.asString ?: return

        if (Database.getServer(keyword) == null) {
            replyT("❌ Server does not exist. Use `!serveradd` instead.")
            return
        }

        val server = createServer(keyword) ?: return
        if (Database.addServer(server)) {
            val id = member?.id
            replyT("✅ Server '$keyword' edited by <@$id>:")
            channel.sendMessage(server.print()).queue()
            logAction("edited server '$keyword'")
        } else {
            replyT("❌ Failed to edit server.", ephemeral = true)
        }
    }

    private fun SlashCommandInteractionEvent.createServer(keyword: String): Server? {
        val displayName = getOption("display_name")?.asString ?: return null
        val inviteLink = getOption("invite_link")?.asString ?: return null
        val description = getOption("description")?.asString ?: return null

        return Server(keyword = keyword, displayName = displayName, inviteLink = inviteLink, description = description)
    }

    private fun SlashCommandInteractionEvent.serverAddAlias() {
        val keyword = getOption("keyword")?.asString ?: return
        val alias = getOption("alias")?.asString ?: return

        if (Database.getServer(alias) != null) {
            replyT("❌ Alias already exists.", ephemeral = true)
            return
        }
        if (Database.getServer(keyword) == null) {
            replyT("❌ Server with keyword '$keyword' does not exist.", ephemeral = true)
            return
        }
        if (Database.addServerAlias(keyword, alias)) {
            replyT("✅ Alias '$alias' added for server '$keyword'")
            logAction("added alias '$alias' for server '$keyword'")
        } else {
            replyT("❌ Failed to add alias.", ephemeral = true)
        }
    }

    private fun SlashCommandInteractionEvent.serverAliasDelete() {
        val keyword = getOption("keyword")?.asString ?: return
        val alias = getOption("alias")?.asString ?: return

        if (Database.deleteServerAlias(keyword, alias)) {
            replyT("✅ Alias '$alias' deleted from server '$keyword'")
            logAction("deleted alias '$alias' for server '$keyword'")

        } else {
            replyT("❌ Failed to delete alias '$alias' for server '$keyword'.", ephemeral = true)
        }
    }

    private fun SlashCommandInteractionEvent.serverDelete() {
        val keyword = getOption("keyword")?.asString ?: return

        if (Database.deleteServer(keyword)) {
            replyT("✅ Server '$keyword' deleted!")
            logAction("deleted server '$keyword'")
        } else {
            replyT("❌ Server with keyword '$keyword' not found or deletion failed.", ephemeral = true)
        }
    }

    private fun SlashCommandInteractionEvent.serverList() { replyT(listServers()) }
}
