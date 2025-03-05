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
import java.io.File

class GitHubClient(owner: String, repo: String, private val token: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val base = "https://api.github.com/repos/$owner/$repo"

    fun findArtifact(lastCommit: String): Artifact? {
        response("$base/actions/artifacts").use {
            if (!it.isSuccessful) error("Error fetching artifacts: ${it.code}")
            val json = it.body?.string() ?: return null
            val response = gson.fromJson(json, ArtifactResponse::class.java)
            return response.artifacts.firstOrNull { artifact ->
                artifact.workflowRun?.headSha == lastCommit && artifact.name == "Development Build"
            }
        }
    }

    fun downloadArtifact(artifactId: Int, outputFile: File) {
        response("$base/actions/artifacts/$artifactId/zip").use {
            if (!it.isSuccessful) error("Artifact download error: ${it.code} - ${it.message}")
            outputFile.writeBytes(it.body?.bytes() ?: ByteArray(0))
        }
    }

    fun findPullRequest(prNumber: Int): PullRequestJson? {
        response("$base/pulls/$prNumber").use {
            val gson = Gson()
            if (!it.isSuccessful) error("GitHub API error: ${it.code}")
            val json = it.body?.string() ?: return null
            return gson.fromJson(json, PullRequestJson::class.java)
        }
    }

    fun getRun(commitSha: String, checkName: String): CheckRun? {
        response("$base/commits/$commitSha/check-runs?check_name=$checkName").use {
            if (!it.isSuccessful) error("Error fetching artifacts: ${it.code}")
            val json = it.body?.string() ?: return null
            val response = gson.fromJson(json, CheckRunsResponse::class.java)
            if (response.totalCount == 0) return null
            return response.checkRuns.firstOrNull()
        }
    }

    // might come handy later
    fun getJob(artifactId: String): Job? {
        response("$base/actions/runs/$artifactId/jobs").use {
            if (!it.isSuccessful) error("Error fetching jobs: ${it.code}")
            val json = it.body?.string() ?: return null
            val response = gson.fromJson(json, JobsResponse::class.java)
            return response.jobs.firstOrNull { job -> job.name == "Build and test" }
        }
    }

    private fun response(url: String): Response {
        val request = Request.Builder().url(url).header("Authorization", "token $token").build()
        return client.newCall(request).execute()
    }
}