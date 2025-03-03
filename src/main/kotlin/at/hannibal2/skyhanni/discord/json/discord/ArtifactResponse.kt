package at.hannibal2.skyhanni.discord.json.discord

import com.google.gson.annotations.SerializedName

data class ArtifactResponse(
    @SerializedName("total_count") val totalCount: Int,
    val artifacts: List<Artifact>
)

data class Artifact(
    val id: Long,
    @SerializedName("node_id") val nodeId: String,
    val name: String,
    @SerializedName("size_in_bytes") val sizeInBytes: Int,
    val url: String,
    @SerializedName("archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean,
    val digest: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("workflow_run") val workflowRun: WorkflowRun
)

data class WorkflowRun(
    val id: Long,
    @SerializedName("repository_id") val repositoryId: Long,
    @SerializedName("head_repository_id") val headRepositoryId: Long,
    @SerializedName("head_branch") val headBranch: String,
    @SerializedName("head_sha") val headSha: String
)
