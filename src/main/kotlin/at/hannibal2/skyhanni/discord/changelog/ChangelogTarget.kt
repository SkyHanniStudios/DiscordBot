package at.hannibal2.skyhanni.discord.changelog

import at.hannibal2.skyhanni.discord.BOT

/** Where /changelog posts. [label] is the command option, [configKey] names the webhook url in config.json. */
enum class ChangelogTarget(val label: String, val configKey: String) {
    PREVIEW("preview", "changelogWebhookPreview"),
    ANNOUNCEMENT("announcement", "changelogWebhookAnnouncement"),
    ;

    /** The text as it gets posted here. The preview marks the title in the first line, that beta is not released yet. */
    fun postedText(text: String): String = if (this == PREVIEW) {
        text.lines().let { (listOf(it.first() + " (preview)") + it.drop(1)).joinToString("\n") }
    } else {
        text
    }

    /**
     * Null when config.json lacks the entry or holds no https url, for example the TODO text of the example config.
     * A "/" at the end of the path is removed, the paths and the token extraction expect the url without it.
     */
    val webhookUrl: String?
        get() = when (this) {
            PREVIEW -> BOT.config.changelogWebhookPreview
            ANNOUNCEMENT -> BOT.config.changelogWebhookAnnouncement
        }?.replace("/?", "?")?.removeSuffix("/")?.takeIf { it.startsWith("https://") }
}
