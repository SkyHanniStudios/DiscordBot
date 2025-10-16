package at.hannibal2.skyhanni.discord.github

import at.hannibal2.skyhanni.discord.json.discord.Artifact
import at.hannibal2.skyhanni.discord.json.discord.ArtifactResponse
import at.hannibal2.skyhanni.discord.json.discord.CheckRun
import at.hannibal2.skyhanni.discord.json.discord.CheckRunsResponse
import at.hannibal2.skyhanni.discord.json.discord.Job
import at.hannibal2.skyhanni.discord.json.discord.JobsResponse
import at.hannibal2.skyhanni.discord.json.discord.PullRequestJson
import at.hannibal2.skyhanni.discord.json.discord.Release
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

    fun findArtifact(lastCommit: String): Artifact? {
        return readJson<ArtifactResponse, Artifact?>("$base/actions/artifacts") { response ->
            response.artifacts.firstOrNull { artifact ->
                artifact.workflowRun?.headSha == lastCommit && artifact.name == "Development Build"
            }
        }
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

    fun getCheckRun(commitSha: String, checkName: String): CheckRun? {
        val url = "$base/commits/$commitSha/check-runs?check_name=$checkName"
        return readJson<CheckRunsResponse, CheckRun?>(url) { response ->
            response.checkRuns.firstOrNull()
        }
    }

    // might come handy later
    fun getJobs(runId: String): List<Job> {
        return readJson<JobsResponse, List<Job>>("$base/actions/runs/$runId/jobs") { response ->
            response.jobs
        } ?: emptyList()
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