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
import java.io.File

class GitHubClient(private val owner: String, private val repo: String, private val token: String) {
    private val client = OkHttpClient()
    private val gson = Gson()

    fun findArtifact(commitSha: String): Artifact? {
        val url = "https://api.github.com/repos/$owner/$repo/actions/artifacts"
        val artifactsRequest = Request.Builder().url(url).header("Authorization", "token $token").build()

        client.newCall(artifactsRequest).execute().use { artifactsResponse ->
            if (!artifactsResponse.isSuccessful) error("Error fetching artifacts: ${artifactsResponse.code}")
            val json = artifactsResponse.body?.string() ?: return null
            val response = gson.fromJson(json, ArtifactResponse::class.java)
            return response.artifacts.firstOrNull {
                it.workflowRun?.headSha == commitSha && it.name == "Development Build"
            }
        }
    }

    fun downloadArtifact(artifactId: Int, outputFile: File) {
        val url = "https://api.github.com/repos/$owner/$repo/actions/artifacts/$artifactId/zip"
        val request = Request.Builder().url(url).header("Authorization", "token $token").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Artifact download error: ${response.code} - ${response.message}")
            outputFile.writeBytes(response.body?.bytes() ?: ByteArray(0))
        }
    }

    fun findPullRequest(prNumber: Int): PullRequestJson? {
        val request = Request.Builder().url("https://api.github.com/repos/$owner/$repo/pulls/$prNumber")
            .header("Authorization", "token $token").build()

        client.newCall(request).execute().use { response ->
            val gson = Gson()
            if (!response.isSuccessful) error("GitHub API error: ${response.code}")
            val json = response.body?.string() ?: return null
            return gson.fromJson(json, PullRequestJson::class.java)
        }
    }

    fun getRun(commitSha: String, checkName: String): CheckRun? {
        val url = "https://api.github.com/repos/$owner/$repo/commits/$commitSha/check-runs?check_name=$checkName"
        val checkRunsRequest = Request.Builder().url(url).header("Authorization", "token $token").build()

        client.newCall(checkRunsRequest).execute().use { checkRunsResponse ->
            if (!checkRunsResponse.isSuccessful) error("Error fetching artifacts: ${checkRunsResponse.code}")
            val json = checkRunsResponse.body?.string() ?: return null
            val response = gson.fromJson(json, CheckRunsResponse::class.java)
            if (response.totalCount == 0) return null
            return response.checkRuns.firstOrNull()
        }
    }

    // might come handy later
    fun getJob(owner: String, repo: String, artifactId: String, token: String): Job? {
        val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$artifactId/jobs"
        val artifactsRequest = Request.Builder().url(url).header("Authorization", "token $token").build()

        client.newCall(artifactsRequest).execute().use { artifactsResponse ->
            if (!artifactsResponse.isSuccessful) error("Error fetching jobs: ${artifactsResponse.code}")
            val json = artifactsResponse.body?.string() ?: return null
            val response = gson.fromJson(json, JobsResponse::class.java)
            return response.jobs.firstOrNull { it.name == "Build and test" }
        }
    }
}