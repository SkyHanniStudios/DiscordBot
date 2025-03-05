package at.hannibal2.skyhanni.discord.github

import at.hannibal2.skyhanni.discord.json.discord.Artifact
import at.hannibal2.skyhanni.discord.json.discord.ArtifactResponse
import at.hannibal2.skyhanni.discord.json.discord.CheckRun
import at.hannibal2.skyhanni.discord.json.discord.CheckRunsResponse
import at.hannibal2.skyhanni.discord.json.discord.Job
import at.hannibal2.skyhanni.discord.json.discord.JobsResponse
import at.hannibal2.skyhanni.discord.json.discord.PullRequestJson
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File

class GitHubClient(owner: String, repo: String, private val token: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val base = "https://api.github.com/repos/$owner/$repo"

    fun findArtifact(lastCommit: String): Artifact? {
        return useJson("$base/actions/artifacts") { json ->
            val response = gson.fromJson(json, ArtifactResponse::class.java)
            response.artifacts.firstOrNull { artifact ->
                artifact.workflowRun?.headSha == lastCommit && artifact.name == "Development Build"
            }
        }
    }

    inline fun <T> useJson(url: String, crossinline block: (String) -> T): T? = use(url) { body ->
        block(body.string())
    }

    fun findArtifact2(lastCommit: String): Artifact? {
        return useJson2<ArtifactResponse>("$base/actions/artifacts") { response ->
            response.artifacts.firstOrNull { artifact ->
                artifact.workflowRun?.headSha == lastCommit && artifact.name == "Development Build"
            }
        }
    }

    inline fun <R, reified T : Any> useJson2(url: String, crossinline block: (T) -> R): R? {
        use(url) { body ->
            block(gson.fromJson(body.string(), T::class.java))
        }
    }

    fun downloadArtifact(artifactId: Int, outputFile: File) {
        use("$base/actions/artifacts/$artifactId/zip") { body ->
            outputFile.writeBytes(body.bytes())
        }
    }

    fun findPullRequest(prNumber: Int): PullRequestJson? {
        return useJson("$base/pulls/$prNumber") { json ->
            gson.fromJson(json, PullRequestJson::class.java)
        }
    }

    fun getRun(commitSha: String, checkName: String): CheckRun? {
        return useJson("$base/commits/$commitSha/check-runs?check_name=$checkName") { json ->
            val response = gson.fromJson(json, CheckRunsResponse::class.java)
            if (response.totalCount == 0) {
                null
            } else {
                response.checkRuns.firstOrNull()
            }
        }
    }

    // might come handy later
    fun getJob(artifactId: String): Job? {
        return useJson("$base/actions/runs/$artifactId/jobs") { json ->
            val response = gson.fromJson(json, JobsResponse::class.java)
            response.jobs.firstOrNull { job -> job.name == "Build and test" }
        }
    }

    private fun <T> use(url: String, block: (ResponseBody) -> T): T? {
        response(url).use {
            if (!it.isSuccessful) error("Error fetching $url - code:${it.code} - message:'${it.message}'")
            val body = it.body ?: error("Error loading '$url' - empty response'")
            return block(body)
        }
    }

    private fun response(url: String): Response {
        val request = Request.Builder().url(url).header("Authorization", "token $token").build()
        return client.newCall(request).execute()
    }
}