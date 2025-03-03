package at.hannibal2.skyhanni.discord.github

import at.hannibal2.skyhanni.discord.json.discord.Artifact
import at.hannibal2.skyhanni.discord.json.discord.PullRequestJson
import at.hannibal2.skyhanni.discord.utils.GithubUtils
import java.io.File

class GitHubClient(private val owner: String, private val repo: String, private val token: String) {

    fun findArtifact(lastCommit: String): Artifact? {
        return GithubUtils.findArtifact(owner, repo, lastCommit, token)
    }

    fun downloadArtifact(artifactId: Long, outputFile: File) {
        GithubUtils.downloadArtifact(owner, repo, artifactId, outputFile, token)
    }

    fun findPullRequest(prNumber: Int): PullRequestJson? {
        return GithubUtils.findPullRequest(owner, repo, prNumber, token)
    }
}