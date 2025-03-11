package at.hannibal2.skyhanni.discord.json.discord

import com.google.gson.annotations.SerializedName

data class CheckRunsResponse (
    @SerializedName("total_count") val totalCount: Long,
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
    @SerializedName("status") val status: RunStatus,
    @SerializedName("conclusion") val conclusion: Conclusion?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("output") val output: Output,
    @SerializedName("name") val name: String,
    @SerializedName("check_suite") val checkSuite: CheckSuite?,
    @SerializedName("app") val app: GitHubapp?,
    @SerializedName("pull_requests") val pullRequests: List<PullRequestMinimal>,
    @SerializedName("deployment") val deployment: Deployment
)

enum class RunStatus {
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
    @SerializedName("annotations_count") val annotationsCount: Long,
    @SerializedName("annotations_url") val annotationsUrl: String
)

data class CheckSuite (
    @SerializedName("id") val id: Long
)

data class GitHubapp (
    @SerializedName("id") val id: Long,
    @SerializedName("slug") val slug: String,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("owner") val owner: SimpleUserOrEnterprise,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("external_url") val externalUrl: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("permissions") val permissions: GitHubPermissions,
    @SerializedName("events") val events: List<String>,
    @SerializedName("installations_count") val installationsCount: Long,
    @SerializedName("client_secret") val clientSecret: String,
    @SerializedName("webhook_secret") val webhookSecret: String?,
    @SerializedName("pem") val pem: String
)

data class SimpleUserOrEnterprise (
    @SerializedName("name") val name: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("login") val login: String?,
    @SerializedName("id") val id: Long,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("gravatar_id") val gravatarId: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("followers_url") val followersUrl: String?,
    @SerializedName("following_url") val followingUrl: String?,
    @SerializedName("gists_url") val gistsUrl: String?,
    @SerializedName("starred_url") val starredUrl: String?,
    @SerializedName("subscriptions_url") val subscriptionsUrl: String?,
    @SerializedName("organizations_url") val organizationsUrl: String?,
    @SerializedName("repos_url") val reposUrl: String?,
    @SerializedName("events_url") val eventsUrl: String?,
    @SerializedName("received_events_url") val receivedEventsUrl: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("site_admin") val siteAdmin: Boolean?,
    @SerializedName("starred_at") val starredAt: String?,
    @SerializedName("user_view_type") val userViewType: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("website_url") val websiteUrl: String?,
    @SerializedName("slug") val slug: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class GitHubPermissions (
    @SerializedName("issues") val issues: String,
    @SerializedName("checks") val checks: String,
    @SerializedName("metadata") val metadata: String,
    @SerializedName("contents") val contents: String,
    @SerializedName("deployments") val deployments: String
)

data class PullRequestMinimal (
    @SerializedName("id") val id: Long,
    @SerializedName("number") val number: Long,
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
    @SerializedName("id") val id: Long,
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
    @SerializedName("production_environment") val productionEnvironment: Boolean,
    @SerializedName("performed_via_github_app") val performedViaGitHubApp: GitHubapp?
)