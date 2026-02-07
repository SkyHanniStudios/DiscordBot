package at.hannibal2.skyhanni.discord.utils

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LiveLog(
    private val channel: MessageChannel,
    private val title: String,
) {
    private var message: Message? = null
    private var status = "Starting..."
    private val logs = mutableListOf<String>()
    private var issueCount = 0
    private val startTime = System.currentTimeMillis()
    private var lastUpdateTime = 0L
    private var hasPendingImportant = false

    private var progressCurrent = 0
    private var progressTotal = 0

    private var autoUpdateJob: ScheduledFuture<*>? = null

    companion object {
        private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    }

    fun startAutoUpdate(intervalMs: Long = 1000L) {
        doUpdate()
        autoUpdateJob = scheduler.scheduleAtFixedRate(
            { doUpdate() },
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    fun stopAutoUpdate() {
        autoUpdateJob?.cancel(false)
        autoUpdateJob = null
    }

    fun progress(current: Int, total: Int) {
        progressCurrent = current
        progressTotal = total
    }

    fun status(text: String) {
        status = text
        maybeUpdate(important = false)
    }

    fun log(text: String) {
        logs.add(text)
        maybeUpdate(important = false)
    }

    fun issue(text: String) {
        logs.add("⚠️ $text")
        issueCount++
        maybeUpdate(important = true)
    }

    private fun maybeUpdate(important: Boolean) {
        if (important) hasPendingImportant = true

        val now = System.currentTimeMillis()
        val interval = if (hasPendingImportant) 1000L else 3000L

        if (message == null || now - lastUpdateTime >= interval) {
            doUpdate()
        }
    }

    private fun doUpdate() {
        val content = render()
        if (message == null) {
            message = channel.sendMessage(content).complete()
        } else {
            message?.editMessage(content)?.queue()
        }
        lastUpdateTime = System.currentTimeMillis()
        hasPendingImportant = false
    }

    private fun render(): String = buildString {
        appendLine("**$title**")
        append("Status: $status")
        if (progressTotal > 0) append(" ($progressCurrent/$progressTotal)")
        if (issueCount > 0) append(" | Issues: $issueCount")
        appendLine(" | Elapsed: ${formatElapsed()}")
        appendLine("```")
        logs.takeLast(12).forEach { appendLine(it) }
        appendLine("```")
    }

    private fun formatElapsed(): String {
        val millis = System.currentTimeMillis() - startTime
        return when {
            millis < 1000 -> "${millis}ms"
            millis < 3000 -> "${"%.1f".format(millis / 1000.0)}s"
            millis < 60000 -> "${millis / 1000}s"
            else -> "${millis / 60000}m ${(millis / 1000) % 60}s"
        }
    }

    fun complete(logMessage: String, status: String = "Done") {
        log(logMessage)
        stopAutoUpdate()
        this.status = status
        progressTotal = 0
        doUpdate()
    }
}