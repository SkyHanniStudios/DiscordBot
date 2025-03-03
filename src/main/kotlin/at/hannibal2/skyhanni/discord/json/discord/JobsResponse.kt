package at.hannibal2.skyhanni.discord.json.discord

data class JobsResponse(
    val total_count: Int,
    val jobs: List<Job>
)

data class Job(
    val id: Long,
    val run_id: Long,
    val workflow_name: String,
    val head_branch: String,
    val run_url: String,
    val run_attempt: Int,
    val node_id: String,
    val head_sha: String,
    val url: String,
    val html_url: String,
    val status: String,
    val conclusion: String,
    val created_at: String,
    val started_at: String,
    val completed_at: String,
    val name: String,
    val steps: List<Step>,
    val check_run_url: String,
    val labels: List<String>,
    val runner_id: Long?,
    val runner_name: String?,
    val runner_group_id: Long?,
    val runner_group_name: String?
)

data class Step(
    val name: String,
    val status: String,
    val conclusion: String,
    val number: Int,
    val started_at: String,
    val completed_at: String
)
