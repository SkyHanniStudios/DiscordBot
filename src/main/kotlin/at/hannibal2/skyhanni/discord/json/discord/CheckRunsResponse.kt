package at.hannibal2.skyhanni.discord.json.discord

import com.google.gson.annotations.SerializedName

data class CheckRunsResponse (
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("check_runs") val checkRuns: List<CheckRun>
)

data class CheckRun (
    @SerializedName("id") val id: Long,
    @SerializedName("head_sha") val headSha: String,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("external_id") val externalId: String?,
    @SerializedName("url") val url: String,
    @SerializedName("html_url") val htmlUrl: String?,
    @SerializedName("details_url") val detailsUrl: String?,
    @SerializedName("status") val status: Status,
    @SerializedName("conclusion") val conclusion: Conclusion?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("output") val output: Output,
    @SerializedName("name") val name: String,
    @SerializedName("check_suite") val checkSuite: CheckSuite?,
    @SerializedName("pull_requests") val pullRequests: List<PullRequestMinimal>,
    @SerializedName("deployment") val deployment: Deployment
)

enum class Status {
    @SerializedName("queued") QUEUED,
    @SerializedName("in_progress") IN_PROGRESS,
    @SerializedName("completed") COMPLETED,
    @SerializedName("waiting") WAITING,
    @SerializedName("requested") REQUESTED,
    @SerializedName("pending") PENDING
}

enum class Conclusion {
    @SerializedName("success") SUCCESS,
    @SerializedName("failure") FAILURE,
    @SerializedName("neutral") NEUTRAL,
    @SerializedName("cancelled") CANCELLED,
    @SerializedName("skipped") SKIPPED,
    @SerializedName("timed_out") TIMED_OUT,
    @SerializedName("action_required") ACTION_REQUIRED
}

data class Output (
    @SerializedName("title") val title: String?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("text") val text: String?,
    @SerializedName("annotations_count") val annotationsCount: Int,
    @SerializedName("annotations_url") val annotationsUrl: String
)

data class CheckSuite (
    @SerializedName("id") val id: Int
)

data class PullRequestMinimal (
    @SerializedName("id") val id: Long,
    @SerializedName("number") val number: Int,
    @SerializedName("url") val url: String,
    @SerializedName("head") val head: MinimalHead,
    @SerializedName("base") val base: MinimalBase
)

data class MinimalHead (
    @SerializedName("ref") val ref: String,
    @SerializedName("sha") val sha: String,
    @SerializedName("repo") val repo: MinimalRepo
)

data class MinimalRepo (
    @SerializedName("id") val id: Long,
    @SerializedName("url") val url: String,
    @SerializedName("name") val name: String
)

data class MinimalBase (
    @SerializedName("ref") val ref: String,
    @SerializedName("sha") val sha: String,
    @SerializedName("repo") val repo: MinimalRepo
)

data class Deployment (
    @SerializedName("url") val url: String,
    @SerializedName("id") val id: Int,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("task") val task: String,
    @SerializedName("original_environment") val originalEnvironment: String,
    @SerializedName("environment") val environment: String,
    @SerializedName("description") val description: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("statuses_url") val statusesUrl: String,
    @SerializedName("repository_url") val repositoryUrl: String,
    @SerializedName("transient_environment") val transientEnvironment: Boolean,
    @SerializedName("production_environment") val productionEnvironment: Boolean
)