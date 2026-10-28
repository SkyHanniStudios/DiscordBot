package at.hannibal2.skyhanni.discord

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

data class BotConfig(
    val token: String,
    val botCommandChannelId: String,
    val allowedServerId: String,
    val githubTokenOwn: String,
    val githubTokenPullRequests: String,
    val jailedRoleId: String,
    val memberRoleId: String,
    val jailedLogChannelId: String,
    /** Null when config.json lacks the entry, [PermissionRole.validateConfig] reports that. */
    val permissionRoleIds: Map<String, String>?,
    // null when config.json lacks the entry, /changelog then names the missing key
    val changelogWebhookPreview: String?,
    val changelogWebhookAnnouncement: String?,
)

object ConfigLoader {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)
    private val exampleConfig = BotConfig(
        "TODO: discord token",
        "TODO: staff channel id",
        "TODO: allowed server id",
        "TODO: github token with sh bot repo access",
        "TODO: github token with sh mod repo access",
        "TODO: role id for the jailed group",
        "TODO: role id for the member group",
        "TODO: channel id for the jailed log channel",
        PermissionRole.entries.associate { it.name to "TODO: role id" },
        "TODO: webhook url of the changelog preview channel",
        "TODO: webhook url of the beta announcement channel",
    )

    fun load(filePath: String): BotConfig {
        try {
            val json = File(filePath).readText()
            return gson.fromJson(json, BotConfig::class.java)
        } catch (ex: Exception) {
            logger.error(
                "Could not load config. Below is an example config:\n```json\n${gson.toJson(exampleConfig)}\n```",
                ex
            )
            exitProcess(1)
        }
    }
}
