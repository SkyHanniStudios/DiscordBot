package at.hannibal2.skyhanni.discord.changelog

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Posts and edits the changelog through a Discord webhook, as a message with components. */
object ChangelogWebhook {

    private const val UNKNOWN_MESSAGE = 10008

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    data class PostedMessage(val messageId: String, val channelId: String)

    private data class MessageJson(
        @SerializedName("id") val id: String,
        @SerializedName("channel_id") val channelId: String,
    )

    private data class ErrorJson(@SerializedName("code") val code: Int?)

    fun send(webhookUrl: String, text: String): PostedMessage = redacting(webhookUrl) {
        val response = request(buildUrl(webhookUrl, "", "wait=true&with_components=true"), "POST", text)
        if (response.statusCode() !in 200..299) error(response.describe())
        response.toPostedMessage()
    }

    /** Null when the message no longer exists. */
    fun edit(webhookUrl: String, messageId: String, text: String): PostedMessage? = redacting(webhookUrl) {
        val response = request(buildUrl(webhookUrl, "/messages/$messageId", "with_components=true"), "PATCH", text)
        when {
            response.statusCode() in 200..299 -> response.toPostedMessage()
            response.statusCode() == 404 && response.errorCode() == UNKNOWN_MESSAGE -> null
            else -> error(response.describe())
        }
    }

    // keeps a query the configured url may already carry, for example thread_id
    internal fun buildUrl(webhookUrl: String, path: String, parameters: String): String {
        val base = webhookUrl.substringBefore('?')
        val query = webhookUrl.substringAfter('?', missingDelimiterValue = "")
        val allParameters = listOf(query, parameters).filter { it.isNotEmpty() }.joinToString("&")
        return "$base$path?$allParameters"
    }

    // flags 32768 marks a message with components, type 10 is a text display, an empty parse list pings nobody
    internal fun buildPayload(text: String): String {
        val textDisplay = JsonObject().apply {
            addProperty("type", 10)
            addProperty("content", text)
        }
        val payload = JsonObject().apply {
            addProperty("flags", 32768)
            add("components", JsonArray().apply { add(textDisplay) })
            add("allowed_mentions", JsonObject().apply { add("parse", JsonArray()) })
        }
        return gson.toJson(payload)
    }

    private fun request(url: String, method: String, text: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("User-Agent", "SkyHanni-Changelog/1.0")
            .method(method, HttpRequest.BodyPublishers.ofString(buildPayload(text)))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun HttpResponse<String>.toPostedMessage(): PostedMessage {
        val message = gson.fromJson(body(), MessageJson::class.java)
        return PostedMessage(message.id, message.channelId)
    }

    private fun HttpResponse<String>.errorCode(): Int? =
        runCatching { gson.fromJson(body(), ErrorJson::class.java)?.code }.getOrNull()

    private fun HttpResponse<String>.describe(): String = "Discord answered ${statusCode()}: ${body()}"

    // the url carries the webhook token, every exception leaving send and edit gets the url and the token replaced
    private fun <T> redacting(webhookUrl: String, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw IllegalStateException(redact("${e.javaClass.simpleName}: ${e.message}", webhookUrl))
    }

    internal fun redact(text: String, webhookUrl: String): String {
        val token = webhookUrl.substringBefore('?').substringAfterLast('/')
        val withoutUrl = text.replace(webhookUrl, "<webhook>")
        return if (token.isEmpty()) withoutUrl else withoutUrl.replace(token, "<webhook>")
    }
}
