package at.hannibal2.skyhanni.discord
import com.google.gson.Gson
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

data class BotConfig(
    val token: String,
    val botCommandChannelId: String,
    val allowedServerId: String,
    val editPermissionRoleIds: Map<String, String>,
)

object ConfigLoader {
    private val gson = GsonBuilder().setPrettyPrinting().create()
	private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)
    val exampleConfig = BotConfig(
		"TODO: discord token",
		"TODO: staff channel id",
		"TODO: allowed server id",
		mapOf(
			"user friendly (non important) name" to "TODO: role id"
		)
	)
	fun load(filePath: String): BotConfig {
	    try {
		    val json = File(filePath).readText()
		    return gson.fromJson(json, BotConfig::class.java)
	    } catch (ex: Exception) {
			logger.error("Could not load config. Below is an example config:\n```json\n${gson.toJson(exampleConfig)}\n```", ex)
		    exitProcess(1)
		}
    }
}

interface Command {
    val category: String
    val permissions: String
    val info: CommandInfo
    fun execute(event: MessageReceivedEvent, args: List<String>)
}

data class CommandInfo(
    val name: String,
    val description: String,
    val options: List<CommandOption> = emptyList(),
    val aliases: List<String> = emptyList(),
)

data class CommandOption(
    val name: String,
    val description: String,
    val required: Boolean,
)