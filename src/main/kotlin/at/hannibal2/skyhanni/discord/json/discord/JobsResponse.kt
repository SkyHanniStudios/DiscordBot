package at.hannibal2.skyhanni.discord.json.discord

import com.google.gson.annotations.SerializedName

data class JobsResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("jobs") val jobs: List<Job>
)

data class Job(
    @SerializedName("id") val id: Long,
    @SerializedName("run_id") val runId: Long,
    @SerializedName("workflow_name") val workflowName: String,
    @SerializedName("head_branch") val headBranch: String,
    @SerializedName("run_url") val runUrl: String,
    @SerializedName("run_attempt") val runAttempt: Int,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("head_sha") val headSha: String,
    @SerializedName("url") val url: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("status") val status: String,
    @SerializedName("conclusion") val conclusion: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("completed_at") val completedAt: String,
    @SerializedName("name") val name: String,
    @SerializedName("steps") val steps: List<Step>,
    @SerializedName("check_run_url") val checkRunUrl: String,
    @SerializedName("labels") val labels: List<String>,
    @SerializedName("runner_id") val runnerId: Long?,
    @SerializedName("runner_name") val runnerName: String?,
    @SerializedName("runner_group_id") val runnerGroupId: Long?,
    @SerializedName("runner_group_name") val runnerGroupName: String?
)

data class Step(
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("conclusion") val conclusion: String,
    @SerializedName("number") val number: Int,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("completed_at") val completedAt: String
)
