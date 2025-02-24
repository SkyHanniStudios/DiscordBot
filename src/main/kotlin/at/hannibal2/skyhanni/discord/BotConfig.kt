package at.hannibal2.skyhanni.discord
import com.google.gson.Gson
import java.io.File

data class BotConfig(
    val token: String,
    val botCommandChannelId: String,
    val allowedServerId: String,
    val editPermissionRoleIds: Map<String, String>,
)

object ConfigLoader {
    private val gson = Gson()
    fun load(filePath: String): BotConfig {
        val json = File(filePath).readText()
        return gson.fromJson(json, BotConfig::class.java)
    }
}