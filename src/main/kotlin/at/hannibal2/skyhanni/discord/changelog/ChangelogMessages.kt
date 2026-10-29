package at.hannibal2.skyhanni.discord.changelog

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Remembers the message posted per target, so a rerun for the same version edits it. Lives next to config.json. */
object ChangelogMessages {

    private val file = File("changelog_messages.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * [threadMessageIds] maps a category name to its message in the thread.
     * Null until the thread is started, empty until the first message is posted into it.
     */
    data class Entry(
        @SerializedName("messageId") val messageId: String,
        @SerializedName("version") val version: String,
        @SerializedName("threadMessageIds") val threadMessageIds: Map<String, String>? = null,
    )

    fun get(target: ChangelogTarget): Entry? = read()[target.label]

    // written to a temp file first, so the target file is never left half written
    fun set(target: ChangelogTarget, entry: Entry) {
        val entries = read() + (target.label to entry)
        val temp = File("${file.path}.tmp")
        temp.writeText(gson.toJson(entries))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun read(): Map<String, Entry> {
        if (!file.exists()) return emptyMap()
        val type = object : TypeToken<Map<String, Entry>>() {}.type
        return try {
            gson.fromJson<Map<String, Entry>>(file.readText(), type)
        } catch (e: JsonParseException) {
            error("${file.name} is corrupted, fix or delete it: ${e.message}")
        } ?: error("${file.name} is empty, fix or delete it")
    }
}
