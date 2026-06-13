package at.hannibal2.skyhanni.discord.json.discord

import com.google.gson.annotations.SerializedName

data class WorkflowRunsResponse(
    @SerializedName("total_count") val totalCount: Long,
    @SerializedName("workflow_runs") val workflowRuns: List<WorkflowRun>,
)

data class WorkflowRun(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String?,
    @SerializedName("head_sha") val headSha: String,
    @SerializedName("status") val status: String?,
    @SerializedName("conclusion") val conclusion: String?,
)
