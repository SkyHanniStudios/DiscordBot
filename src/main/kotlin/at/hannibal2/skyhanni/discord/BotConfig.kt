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
    val editPermissionRoleIds: LinkedHashMap<String, String>,
	val scamKeywordConfig: List<ConfigLoader.ScamKeywordConfig>
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
		linkedMapOf(
			"user friendly (non important) name" to "TODO: role id"
		),
		scamKeywordConfig = listOf(
			ScamKeywordConfig(
				keyWords = setOf(
					"mrbeast",
					"beast",
					"$",
					"crypto",
					"casino",
					"promo",
					"reward",
					"withdraw",
					"claim",
					"celebrate",
				) ,
				requiredFindings = 6
			)
		)
	)
	fun load(filePath: String): BotConfig {
	    try {
		    val json = File(filePath).readText()
		    val config= gson.fromJson(json, BotConfig::class.java)
            if (config.scamKeywordConfig.any { it.requiredFindings==0 }) {
                error("requiredFindings must be greater than 0 in scamKeywordConfig.")
            }
			return config
	    } catch (ex: Exception) {
			logger.error("Could not load config. Below is an example config:\n```json\n${gson.toJson(exampleConfig)}\n```", ex)
		    exitProcess(1)
		}
    }

	data class ScamKeywordConfig(
		val keyWords: Set<String>,
		val requiredFindings: Int
	){
		init {
			error("requiredFindings must be greater than 0 in scamKeywordConfig.")
		}
	}
}
