package at.hannibal2.skyhanni.discord.json.discord

import com.google.gson.annotations.SerializedName

data class ArtifactResponse (
    @SerializedName("total_count") val totalCount: Long,
    @SerializedName("artifacts") val artifacts: List<Artifact>
)

data class Artifact (
    @SerializedName("id") val id: Long,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("name") val name: String,
    @SerializedName("size_in_bytes") val sizeInBytes: Long,
    @SerializedName("url") val url: String,
    @SerializedName("archive_download_url") val archiveDownloadUrl: String,
    @SerializedName("expired") val expired: Boolean,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("digest") val digest: String?,
    @SerializedName("workflow_run") val workflowRun: MinimalWorkflowRun?
)

data class MinimalWorkflowRun (
    @SerializedName("id") val id: Long,
    @SerializedName("repository_id") val repositoryId: Long,
    @SerializedName("head_repository_id") val headRepositoryId: Long,
    @SerializedName("head_branch") val headBranch: String,
    @SerializedName("head_sha") val headSha: String
)