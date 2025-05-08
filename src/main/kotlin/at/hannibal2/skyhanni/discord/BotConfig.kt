package at.hannibal2.skyhanni.discord

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

data class BotConfig(
    val token: String,
    val botCommandChannelId: String,
    val moderationChannelId: String,
    val allowedServerId: String,
    val githubTokenOwn: String,
    val githubTokenPullRequests: String,
    val editPermissionRoleIds: Map<String, String>,
)

object ConfigLoader {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)
    private val exampleConfig = BotConfig(
        "TODO: discord token",
        "TODO: staff channel id",
        "TODO: moderation channel id",
        "TODO: allowed server id",
        "TODO: github token with sh bot repo access",
        "TODO: github token with sh mod repo access",
        mapOf(
            "user friendly (non important) name" to "TODO: role id"
        )
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