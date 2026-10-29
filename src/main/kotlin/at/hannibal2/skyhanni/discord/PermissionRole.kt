package at.hannibal2.skyhanni.discord

/**
 * Roles that unlock bot commands. The [name] of an entry is its key in [BotConfig.permissionRoleIds].
 * A role passes every check for the roles below it.
 */
enum class PermissionRole {
    ADMIN,
    DISCORD_MODERATOR,
    COMMUNITY_HELPER,
    CODE_REVIEWER,
    CONTRIBUTOR,
    ;

    val parent: PermissionRole?
        get() = when (this) {
            ADMIN -> null
            DISCORD_MODERATOR -> ADMIN
            COMMUNITY_HELPER -> DISCORD_MODERATOR
            CODE_REVIEWER -> ADMIN
            CONTRIBUTOR -> CODE_REVIEWER
        }

    fun withHigherRoles(): Sequence<PermissionRole> = generateSequence(this) { it.parent }

    companion object {

        /** Reports problems with [BotConfig.permissionRoleIds] in the bot channel. Call it once JDA is ready. */
        fun validateConfig() {
            val problems = findConfigProblems()
            if (problems.isEmpty()) return
            val lines = listOf("Problems with `permissionRoleIds` in config.json:") + problems.map { "- $it" }
            Utils.sendMessageToBotChannel(lines)
        }

        private fun findConfigProblems(): List<String> {
            val roleIds = BOT.config.permissionRoleIds
                ?: return listOf("The entry is missing, nobody can use role restricted commands.")
            val guild = BOT.jda.getGuildById(BOT.config.allowedServerId)
            val problems = mutableListOf<String>()

            for (key in roleIds.keys - entries.map { it.name }.toSet()) {
                problems.add("Unknown key `$key`.")
            }
            for (role in entries) {
                val id = roleIds[role.name]
                if (id == null) {
                    problems.add("No role id for `${role.name}`.")
                    continue
                }
                // getRoleById(String) throws for ids that are no number
                val exists = id.toLongOrNull()?.let { guild?.getRoleById(it) } != null
                if (guild != null && !exists) {
                    problems.add("Role id `$id` for `${role.name}` does not exist on the server.")
                }
            }
            return problems
        }
    }
}
