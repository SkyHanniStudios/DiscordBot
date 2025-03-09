package at.hannibal2.skyhanni.discord.json.discord

import com.google.gson.annotations.SerializedName

data class JobsResponse (
    @SerializedName("total_count") val totalCount: Long,
    @SerializedName("jobs") val jobs: List<Job>
)

data class Job (
    @SerializedName("id") val id: Long,
    @SerializedName("run_id") val runId: Long,
    @SerializedName("run_url") val runUrl: String,
    @SerializedName("run_attempt") val runAttempt: Long,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("head_sha") val headSha: String,
    @SerializedName("url") val url: String,
    @SerializedName("html_url") val htmlUrl: String?,
    @SerializedName("status") val status: RunStatus,
    @SerializedName("conclusion") val conclusion: Conclusion?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("name") val name: String,
    @SerializedName("steps") val steps: List<Step>,
    @SerializedName("check_run_url") val checkRunUrl: String,
    @SerializedName("labels") val labels: List<String>,
    @SerializedName("runner_id") val runnerId: Long?,
    @SerializedName("runner_name") val runnerName: String?,
    @SerializedName("runner_group_id") val runnerGroupId: Long?,
    @SerializedName("runner_group_name") val runnerGroupName: String?,
    @SerializedName("workflow_name") val workflowName: String?,
    @SerializedName("head_branch") val headBranch: String?
)

data class Step (
    @SerializedName("status") val status: StepStatus,
    @SerializedName("conclusion") val conclusion: String?,
    @SerializedName("name") val name: String,
    @SerializedName("number") val number: Long,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("completed_at") val completedAt: String?
)

enum class StepStatus {
    @SerializedName("queued") QUEUED,
    @SerializedName("in_progress") IN_PROGRESS,
    @SerializedName("completed") COMPLETED
}
