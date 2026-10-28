package at.hannibal2.skyhanni.discord.json.discord

import com.google.gson.annotations.SerializedName

// not called Tag, that name belongs to the tag commands
data class GitTag(
    @SerializedName("name") val name: String,
)
