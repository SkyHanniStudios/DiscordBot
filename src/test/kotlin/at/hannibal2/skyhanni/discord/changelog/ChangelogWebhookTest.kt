package at.hannibal2.skyhanni.discord.changelog

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangelogWebhookTest {

    private val webhook = "https://discord.com/api/webhooks/123/secretToken"

    @Test
    fun `payload is one text display without mentions`() {
        assertEquals(
            """{"flags":32768,"components":[{"type":10,"content":"## Title\n\"quoted\" <link>"}],"allowed_mentions":{"parse":[]}}""",
            ChangelogWebhook.buildPayload("## Title\n\"quoted\" <link>"),
        )
    }

    @Test
    fun `url without query gets the parameters`() {
        assertEquals(
            "$webhook?wait=true&with_components=true",
            ChangelogWebhook.buildUrl(webhook, "", "wait=true&with_components=true"),
        )
    }

    @Test
    fun `url keeps an existing query behind the path`() {
        assertEquals(
            "$webhook/messages/9?thread_id=5&with_components=true",
            ChangelogWebhook.buildUrl("$webhook?thread_id=5", "/messages/9", "with_components=true"),
        )
    }

    @Test
    fun `redact removes the url and the token`() {
        assertEquals(
            "failed for <webhook>/messages/9, token <webhook>",
            ChangelogWebhook.redact("failed for $webhook/messages/9, token secretToken", webhook),
        )
    }
}
