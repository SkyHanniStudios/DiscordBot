package at.hannibal2.skyhanni.discord.github

import at.hannibal2.skyhanni.discord.json.discord.*
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.util.*

private const val PAGE_SIZE = 100

class GitHubClient(user: String, repo: String, private val token: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val base = "https://api.github.com/repos/$user/$repo"
    private val actionsRunUrlPattern = Regex("""/actions/runs/(?<runId>\d+)""")

    /**
     * Returns an arbitrary Minecraft version, since a multi version build uploads one jar per version.
     */
    fun findArtifact(lastCommit: String): Artifact? {
        return findArtifactsForCommit(lastCommit).firstOrNull()
    }

    fun findArtifactsForRun(runId: String): List<Artifact> = readAllPages<ArtifactResponse, Artifact>(
        "$base/actions/runs/$runId/artifacts",
        totalCount = { it.totalCount },
        items = { it.artifacts },
    )

    private fun findArtifactsForCommit(lastCommit: String): List<Artifact> =
        readAllPages<ArtifactResponse, Artifact>(
            "$base/actions/artifacts",
            totalCount = { it.totalCount },
            items = { it.artifacts },
        ).filter { it.workflowRun?.headSha == lastCommit && ArtifactNames.isSkyHanniJar(it.name) }

    fun downloadArtifact(artifactId: Long, outputFile: File) {
        readBody("$base/actions/artifacts/$artifactId/zip") { body ->
            outputFile.writeBytes(body.bytes())
        }
    }

    fun findPullRequest(prNumber: Int): PullRequestJson? {
        return readJson<PullRequestJson, PullRequestJson?>("$base/pulls/$prNumber") { it }
    }

    fun getFileContent(filePath: String, branch: String = "master"): String? {
        val url = "$base/contents/$filePath?ref=$branch"
        return readJson<Map<String, Any>, String?>(url) { response ->
            val content = response["content"] as? String
            val encoding = response["encoding"] as? String
            if (content != null && encoding == "base64") {
                String(Base64.getMimeDecoder().decode(content))
            } else null
        }
    }

    fun getRun(commitSha: String, checkName: String): CheckRun? {
        val checkRuns = readAllPages<CheckRunsResponse, CheckRun>(
            "$base/commits/$commitSha/check-runs",
            totalCount = { it.totalCount },
            items = { it.checkRuns },
        )
        return selectCheckRun(checkRuns, checkName)
    }

    fun isWorkflowApprovalRequired(commitSha: String): Boolean {
        val workflowRuns = readAllPages<WorkflowRunsResponse, WorkflowRun>(
            "$base/actions/runs?head_sha=$commitSha",
            totalCount = { it.totalCount },
            items = { it.workflowRuns },
        )
        return hasWorkflowRunNeedingApproval(workflowRuns)
    }

    // might come handy later
    fun getJob(artifactId: String): Job? {
        return readJson<JobsResponse, Job?>("$base/actions/runs/$artifactId/jobs") { response ->
            response.jobs.firstOrNull { job -> job.name.matchesCheckName("Build and test") }
        }
    }

    internal fun selectCheckRun(checkRuns: List<CheckRun>, checkName: String): CheckRun? {
        val matchingChecks = checkRuns.filter { it.name.matchesCheckName(checkName) }
        val latestRunChecks = matchingChecks
            .groupBy { it.actionsRunId() ?: it.checkSuite?.id?.toString() ?: it.id.toString() }
            .maxByOrNull { (_, checks) -> checks.maxOf { it.id } }
            ?.value
            .orEmpty()

        return latestRunChecks
            .filter { it.status != RunStatus.COMPLETED }
            .maxWithOrNull(compareBy<CheckRun> { it.status.incompletePriority() }.thenBy { it.id })
            ?: latestRunChecks
                .filter { it.conclusion != Conclusion.SUCCESS }
                .maxByOrNull { it.id }
            ?: latestRunChecks
                .maxWithOrNull(compareBy<CheckRun> { it.completedAt ?: "" }.thenBy { it.id })
    }

    internal fun hasWorkflowRunNeedingApproval(workflowRuns: List<WorkflowRun>): Boolean {
        return workflowRuns.any { workflowRun ->
            workflowRun.status == "action_required" || workflowRun.conclusion == "action_required"
        }
    }

    private fun CheckRun.actionsRunId(): String? {
        return listOfNotNull(htmlUrl, detailsUrl).firstNotNullOfOrNull { url ->
            actionsRunUrlPattern.find(url)?.groups?.get("runId")?.value
        }
    }

    private fun String.matchesCheckName(checkName: String): Boolean {
        return this == checkName || startsWith("$checkName (")
    }

    private fun RunStatus.incompletePriority(): Int = when (this) {
        RunStatus.IN_PROGRESS -> 5
        RunStatus.QUEUED -> 4
        RunStatus.REQUESTED -> 3
        RunStatus.WAITING -> 2
        RunStatus.PENDING -> 1
        RunStatus.COMPLETED -> 0
    }

    /**
     * Reads every page of a paginated GitHub list endpoint. [url] may or may not already contain a query
     * string, the pagination parameters are appended with the matching separator.
     */
    private inline fun <reified T : Any, E> readAllPages(
        url: String,
        totalCount: (T) -> Long,
        items: (T) -> List<E>,
    ): List<E> {
        val separator = if ('?' in url) '&' else '?'
        val result = mutableListOf<E>()
        var page = 1

        while (true) {
            val response = readJson<T, T>("$url${separator}per_page=$PAGE_SIZE&page=$page") { it } ?: break
            val pageItems = items(response)
            result += pageItems
            if (pageItems.isEmpty() || totalCount(response) <= page * PAGE_SIZE) break
            page++
        }

        return result
    }

    private inline fun <reified T : Any, R> readJson(url: String, crossinline block: (T) -> R): R? =
        readBody(url) { body ->
            val type = object : com.google.gson.reflect.TypeToken<T>() {}.type
            block(gson.fromJson(body.string(), type))
        }

    inline fun <T> readBody(url: String, block: (ResponseBody) -> T): T? {
        response(url).use {
            if (!it.isSuccessful) {
                error("Error fetching $url - code:${it.code} - message:'${it.message}' '${it.body?.string()}'")
            }
            val body = it.body ?: error("Error loading '$url' - empty response'")
            return block(body)
        }
    }

    fun response(url: String): Response {
        val request = Request.Builder().url(url).header("Authorization", "token $token").build()
        return client.newCall(request).execute()
    }

    fun getReleases(): List<Release>? {
        val url = "$base/releases"
        return readJson<List<Release>, List<Release>?>(url) { it }
    }
}
