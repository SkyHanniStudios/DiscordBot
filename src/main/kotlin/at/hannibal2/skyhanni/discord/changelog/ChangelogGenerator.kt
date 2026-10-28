package at.hannibal2.skyhanni.discord.changelog

import at.hannibal2.changelog.ModVersion
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

object ChangelogGenerator {

    private const val TIMEOUT_MINUTES = 2L
    private const val KILL_WAIT_SECONDS = 10L
    private const val MAX_ERROR_LINES = 10
    private const val DEBUG_LINE = "erroring on line"
    private val blockingPrefixes = listOf("PR has incorrect name: ", "PR has errors: ")

    /**
     * [messages] holds what the builder printed before its outputs, without its debug lines and the blank lines at both ends.
     * [hasBlockingErrors] is set when one of those lines starts with "PR has incorrect name: " or "PR has errors: ".
     */
    data class Result(
        val text: String,
        val messages: List<String>,
        val summary: List<String>,
        val hasBlockingErrors: Boolean,
    )

    /**
     * Runs the builder through [ChangelogRunner] in its own JVM and blocks until it ends or the timeout kills it.
     * [onStarted] runs once the process is running.
     */
    fun generate(version: ModVersion, previousVersion: ModVersion?, onStarted: () -> Unit): Result {
        val files = mutableListOf<File>()
        var process: Process? = null
        try {
            val stdout = createTempFile(".out", files)
            val stderr = createTempFile(".err", files)
            process = ProcessBuilder(buildCommand(version, previousVersion))
                .redirectOutput(stdout)
                .redirectError(stderr)
                .start()
            onStarted()
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                // bounded, a child in uninterruptible sleep must not keep the command from answering
                process.destroyForcibly().waitFor(KILL_WAIT_SECONDS, TimeUnit.SECONDS)
                error("Changelog builder timed out after $TIMEOUT_MINUTES minutes:\n${selectErrorLines(stderr.readLines())}")
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                error("Changelog builder failed with exit code $exitCode:\n${selectErrorLines(stderr.readLines())}")
            }
            return parse(stdout.readLines())
        } finally {
            // also ends the child when an exception or an interrupt leaves this function early
            process?.let { if (it.isAlive) it.destroyForcibly() }
            files.forEach { it.delete() }
        }
    }

    // deleteOnExit as well, a shutdown during a run never reaches the finally of generate
    private fun createTempFile(suffix: String, files: MutableList<File>): File =
        Files.createTempFile("changelog-", suffix).toFile().also {
            it.deleteOnExit()
            files.add(it)
        }

    // passes the classpath of this JVM on, so the child loads the same builder version as the bot
    private fun buildCommand(version: ModVersion, previousVersion: ModVersion?): List<String> = buildList {
        add(File(System.getProperty("java.home"), "bin/java").path)
        add("-Dstdout.encoding=UTF-8")
        add("-Dstderr.encoding=UTF-8")
        add("-cp")
        add(System.getProperty("java.class.path"))
        add(ChangelogRunner::class.java.name)
        add(version.toString())
        previousVersion?.let { add(it.toString()) }
    }

    // a stack trace ends in frames, the exception and "Caused by" lines are the unindented ones
    internal fun selectErrorLines(lines: List<String>): String {
        val nonBlank = lines.filter { it.isNotBlank() }
        val exceptionLines = nonBlank.filter { !it.first().isWhitespace() }
        val shown = exceptionLines.ifEmpty { nonBlank.takeLast(MAX_ERROR_LINES) }.take(MAX_ERROR_LINES)
        return shown.joinToString("\n").ifEmpty { "nothing on stderr" }
    }

    internal fun parse(lines: List<String>): Result {
        val firstOutput = lines.indexOfFirst { it.startsWith("Output type: ") }.takeIf { it != -1 }
            ?: error("No output type found in the changelog builder output")
        val publicOutput = lines.indexOf("Output type: DISCORD_PUBLIC").takeIf { it != -1 }
            ?: error("No DISCORD_PUBLIC output found in the changelog builder output")
        // everything after the public text is read as the summary
        if (lines.indexOfLast { it.startsWith("Output type: ") } != publicOutput) {
            error("DISCORD_PUBLIC is not the last output of the changelog builder")
        }
        val textStart = lines.indexOfBorder(after = publicOutput)
        val textEnd = lines.indexOfBorder(after = textStart)

        val messages = lines.subList(0, firstOutput).filter { it != DEBUG_LINE }.trimBlankLines()
        return Result(
            text = lines.subList(textStart + 1, textEnd).joinToString("\n"),
            messages = messages,
            summary = lines.subList(textEnd + 1, lines.size).trimBlankLines(),
            hasBlockingErrors = messages.any { line -> blockingPrefixes.any { line.startsWith(it) } },
        )
    }

    private fun List<String>.indexOfBorder(after: Int): Int =
        (after + 1 until size).firstOrNull { this[it].isBorder() }
            ?: error("Missing border line in the changelog builder output")

    private fun String.isBorder(): Boolean = isNotEmpty() && all { it == '=' }

    private fun List<String>.trimBlankLines(): List<String> = dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
}
