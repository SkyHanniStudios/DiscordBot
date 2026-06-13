package at.hannibal2.skyhanni.discord.github

import at.hannibal2.skyhanni.discord.json.discord.Artifact
import at.hannibal2.skyhanni.discord.json.discord.ArtifactResponse
import at.hannibal2.skyhanni.discord.json.discord.CheckRun
import at.hannibal2.skyhanni.discord.json.discord.CheckRunsResponse
import at.hannibal2.skyhanni.discord.json.discord.Conclusion
import at.hannibal2.skyhanni.discord.json.discord.Job
import at.hannibal2.skyhanni.discord.json.discord.JobsResponse
import at.hannibal2.skyhanni.discord.json.discord.PullRequestJson
import at.hannibal2.skyhanni.discord.json.discord.Release
import at.hannibal2.skyhanni.discord.json.discord.RunStatus
import at.hannibal2.skyhanni.discord.json.discord.WorkflowRun
import at.hannibal2.skyhanni.discord.json.discord.WorkflowRunsResponse
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.util.Base64

class GitHubClient(user: String, repo: String, private val token: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val base = "https://api.github.com/repos/$user/$repo"
    private val actionsRunUrlPattern = Regex("""/actions/runs/(?<runId>\d+)""")

    fun findArtifact(lastCommit: String): Artifact? {
        return findArtifactsForCommit(lastCommit).firstOrNull()
    }

    fun findArtifactsForRun(runId: String): List<Artifact> {
        return findArtifacts("$base/actions/runs/$runId/artifacts?per_page=100&page=") { true }
    }

    private fun findArtifactsForCommit(lastCommit: String): List<Artifact> {
        return findArtifacts("$base/actions/artifacts?per_page=100&page=") { artifact ->
            artifact.workflowRun?.headSha == lastCommit
        }
    }

    private fun findArtifacts(urlPrefix: String, artifactPredicate: (Artifact) -> Boolean): List<Artifact> {
        val artifacts = mutableListOf<Artifact>()
        var page = 1

        do {
            val response = readJson<ArtifactResponse, ArtifactResponse>("$urlPrefix$page") { it } ?: break

            artifacts += response.artifacts.filter { artifact ->
                artifactPredicate(artifact) && ArtifactNames.isSkyHanniJarArtifact(artifact.name)
            }

            page++
        } while (response.artifacts.isNotEmpty() && response.totalCount > (page - 1) * 100)

        return artifacts
    }

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
        val checkRuns = mutableListOf<CheckRun>()
        var page = 1

        do {
            val response = readJson<CheckRunsResponse, CheckRunsResponse>(
                "$base/commits/$commitSha/check-runs?per_page=100&page=$page",
            ) { it } ?: break

            checkRuns += response.checkRuns
            page++
        } while (response.checkRuns.isNotEmpty() && response.totalCount > checkRuns.size)

        return selectCheckRun(checkRuns, checkName)
    }

    fun isWorkflowApprovalRequired(commitSha: String): Boolean {
        val workflowRuns = mutableListOf<WorkflowRun>()
        var page = 1

        do {
            val response = readJson<WorkflowRunsResponse, WorkflowRunsResponse>(
                "$base/actions/runs?head_sha=$commitSha&per_page=100&page=$page",
            ) { it } ?: break

            workflowRuns += response.workflowRuns
            if (hasWorkflowRunNeedingApproval(response.workflowRuns)) return true

            page++
        } while (response.workflowRuns.isNotEmpty() && response.totalCount > workflowRuns.size)

        return false
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
