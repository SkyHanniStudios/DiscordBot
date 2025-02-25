package at.hannibal2.skyhanni.discord

import at.hannibal2.skyhanni.discord.Utils.reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

@Suppress("UNUSED_PARAMETER")
class TagCommands(private val config: BotConfig, commands: Commands) {

    init {
        commands.add(Command("list", ::listCommand))
        commands.add(Command("taglist", ::listCommand))

        commands.add(Command("edit", ::editCommand))
        commands.add(Command("change", ::editCommand))

        commands.add(Command("add", ::addCommand))
        commands.add(Command("delete", ::deleteCommand))
        commands.add(Command("remove", ::deleteCommand))
    }

    private fun listCommand(event: MessageReceivedEvent, args: List<String>) {
        val keywords = Database.listKeywords().joinToString(", ")
        val replyMsg = if (keywords.isNotEmpty()) "📌 Keywords: $keywords" else "No keywords set."
        event.reply(replyMsg)
    }

    private fun addCommand(event: MessageReceivedEvent, args: List<String>) {
        if (!hasEditPermission(event)) return

        if (args.size < 3) return
        val keyword = args[1]
        val response = args[2]
        if (Database.listKeywords().contains(keyword.lowercase())) {
            event.reply("❌ Already exists. Use `!edit` instead.")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            event.reply("✅ Keyword '$keyword' added!")
        } else {
            event.reply("❌ Failed to add keyword.")
        }
    }

    private fun editCommand(event: MessageReceivedEvent, args: List<String>) {
        if (event.channel.id != config.botCommandChannelId) return
        if (!hasEditPermission(event)) return

        if (args.size < 3) return
        val keyword = args[1]
        val response = args[2]
        val oldResponse = Database.getResponse(keyword)
        if (oldResponse == null) {
            event.reply("❌ Keyword doesn't exist! Use `!add` instead.")
            return
        }
        if (Database.addKeyword(keyword, response)) {
            event.reply("✅ Keyword '$keyword' edited!")
        } else {
            event.reply("❌ Failed to edit keyword.")
        }
    }

    private fun deleteCommand(event: MessageReceivedEvent, args: List<String>) {
        if (event.channel.id != config.botCommandChannelId) return
        if (!hasEditPermission(event)) return

        if (args.size < 2) return
        val keyword = args[1]
        if (Database.deleteKeyword(keyword)) {
            event.reply("🗑️ Keyword '$keyword' deleted!")
        } else {
            event.reply("❌ Keyword '$keyword' not found.")
        }
    }

    fun handleTag(event: MessageReceivedEvent): Boolean {
        val message = event.message
        var keyword = message.contentRaw.substring(1)
        var deleting = false
        if (keyword.endsWith(" -d")) {
            keyword = keyword.dropLast(3)
            deleting = true
        }
        val response = Database.getResponse(keyword) ?: run {
            if (hasEditPermission(event)) {
                val s = "Unknown command \uD83E\uDD7A Type `!help` for help."
                event.reply(s)
            }
            return false
        }

        message.referencedMessage?.let {
            message.delete().queue()
            it.reply(response).queue()
        } ?: run {
            if (deleting) {
                message.delete().queue { event.channel.sendMessage(response).queue() }
            } else {
                message.reply(response).queue()
            }
        }
        return true
    }

    private fun hasEditPermission(event: MessageReceivedEvent): Boolean {
        if (event.channel.id != config.botCommandChannelId) return false
        val member = event.member ?: return false
        val allowedRoleIds = config.editPermissionRoleIds.values
        if (member.roles.none { it.id in allowedRoleIds }) {
            event.reply("No perms \uD83E\uDD7A")
            return false
        }
        return true
    }

}