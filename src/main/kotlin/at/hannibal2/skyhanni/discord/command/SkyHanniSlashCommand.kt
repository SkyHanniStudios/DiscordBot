package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.PermissionRole
import net.dv8tion.jda.api.interactions.commands.OptionType

/**
 * Annotate a class to "AutoService it" as a Slash Command to the bot.
 * @property name The name of the command (case)
 * @property description The description of the command, displayed in Discord.
 * @property aliases Alternative names for the command, registered as aliases.
 * @property parameters List of [SkyHanniSlashCommandParameter] for the command.
 *  "Map down to" discord's slash params.
 * @property allowedRoles Members need one of these roles, or a role above one of them.
 *  Empty lets everyone use the command.
 * @property supportsSlash the supports slash
 */
@Target(AnnotationTarget.CLASS)
annotation class SkyHanniSlashCommand(
    val name: String,
    val description: String,
    val aliases: Array<String> = [],
    val parameters: Array<SkyHanniSlashCommandParameter> = [],
    val allowedRoles: Array<PermissionRole> = [],
    val supportsSlash: Boolean = true,
)

/**
 * @property name Name of the parameter. MUST be unique per-command.
 * @property description Optional description for the parameter.
 * @property required User must fill in this parameter.
 * @property autoComplete Whether autocomplete is ENABLED or not. This does NOT guarantee that it "will work".
 * @property type The parameter [OptionType], as defined in JDA
 */
annotation class SkyHanniSlashCommandParameter(
    val name: String,
    val description: String = "",
    val required: Boolean = true,
    val autoComplete: Boolean = false,
    val type: OptionType = OptionType.STRING,
)
