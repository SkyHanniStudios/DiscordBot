package at.hannibal2.skyhanni.discord.changelog

import at.hannibal2.changelog.PullRequestCategory
import kotlin.test.*

class ChangelogGeneratorTest {

    private val border = "=".repeat(81)
    private val wrongTitle =
        "PR has incorrect name: #1 - Wrong title by someone (https://github.com/SkyHanniStudios/SkyHanni/pull/1)"
    private val excluded =
        "Excluded PR: #2 - Backend: Cleanup by someone (https://github.com/SkyHanniStudios/SkyHanni/pull/2)"

    private val publicText = listOf(
        "## SkyHanni Beta 9.5.0",
        "### New Features",
        "```diff",
        "+ Added something. - someone",
        "```",
    )

    private val internalText = listOf(
        "## SkyHanni Beta 9.5.0",
        "### New Features",
        "- Added something. - someone [PR](<https://github.com/SkyHanniStudios/SkyHanni/pull/3>)",
        "### Fixes",
        "- Fixed something. - someone [PR](<https://github.com/SkyHanniStudios/SkyHanni/pull/4>)",
        "  -  Extra info.",
    )

    // follows the println calls in SkyHanniChangelogBuilder.generateChangelog
    private fun output(messages: List<String>): List<String> = listOf("erroring on line", "") + messages + listOf(
        "",
        "Output type: DISCORD_INTERNAL",
        "40/2000 characters used",
        border,
    ) + internalText + listOf(
        border,
        "",
        "Output type: GITHUB",
        border,
        "## SkyHanni Beta 9.5.0",
        border,
        "",
        "Output type: DISCORD_PUBLIC",
        "120/2000 characters used",
        border,
    ) + publicText + listOf(
        border,
        "",
        "Loaded 2 PRs to be processed for changelog",
        "Processed 1/2 PRs for changelog",
    )

    @Test
    fun `reads the public text between its borders`() {
        val result = ChangelogGenerator.parse(output(listOf(excluded)))
        assertEquals(publicText.joinToString("\n"), result.text)
    }

    @Test
    fun `splits the internal text into its categories without the title`() {
        assertEquals(
            mapOf(
                PullRequestCategory.FEATURE to internalText.subList(1, 3).joinToString("\n"),
                PullRequestCategory.FIX to internalText.subList(3, 6).joinToString("\n"),
            ),
            ChangelogGenerator.parse(output(listOf(excluded))).internalSections,
        )
    }

    @Test
    fun `keeps the messages without the debug line`() {
        val messages = listOf(
            wrongTitle,
            "  - PR title does not match the expected format of 'Category: Title'",
            "",
            "",
            excluded,
        )
        assertEquals(messages, ChangelogGenerator.parse(output(messages)).messages)
    }

    @Test
    fun `reads the summary after the public text`() {
        val result = ChangelogGenerator.parse(output(listOf(excluded)))
        assertEquals(
            listOf("Loaded 2 PRs to be processed for changelog", "Processed 1/2 PRs for changelog"),
            result.summary,
        )
    }

    @Test
    fun `a wrong title blocks`() {
        assertTrue(ChangelogGenerator.parse(output(listOf(wrongTitle, excluded))).hasBlockingErrors)
    }

    @Test
    fun `an excluded pull request does not block`() {
        assertFalse(ChangelogGenerator.parse(output(listOf(excluded))).hasBlockingErrors)
    }

    @Test
    fun `fails without public output`() {
        val lines = output(listOf(excluded)).takeWhile { it != "Output type: DISCORD_PUBLIC" }
        assertFailsWith<IllegalStateException> { ChangelogGenerator.parse(lines) }
    }

    @Test
    fun `fails without closing border`() {
        val lines = output(listOf(excluded)).dropLast(4)
        assertFailsWith<IllegalStateException> { ChangelogGenerator.parse(lines) }
    }

    @Test
    fun `error lines keep the exception and its causes`() {
        val stderr = listOf(
            "Exception in thread \"main\" java.lang.IllegalStateException: outer",
            "\tat Foo.bar(Foo.kt:1)",
            "Caused by: java.io.IOException: inner",
            "\tat Foo.baz(Foo.kt:2)",
            "\t... 3 more",
        )
        assertEquals(
            "Exception in thread \"main\" java.lang.IllegalStateException: outer\nCaused by: java.io.IOException: inner",
            ChangelogGenerator.selectErrorLines(stderr),
        )
    }

    @Test
    fun `error lines fall back to the last lines`() {
        val stderr = (1..12).map { "\tline $it" }
        assertEquals((3..12).joinToString("\n") { "\tline $it" }, ChangelogGenerator.selectErrorLines(stderr))
    }

    @Test
    fun `error lines name an empty stderr`() {
        assertEquals("nothing on stderr", ChangelogGenerator.selectErrorLines(emptyList()))
    }

    @Test
    fun `fails when the public output is not the last one`() {
        val lines = output(listOf(excluded)) + listOf("", "Output type: GITHUB", border, "github text", border)
        assertFailsWith<IllegalStateException> { ChangelogGenerator.parse(lines) }
    }
}
