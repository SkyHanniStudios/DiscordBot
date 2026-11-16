package at.hannibal2.skyhanni.discord.github

import at.hannibal2.skyhanni.discord.json.discord.CheckRun
import at.hannibal2.skyhanni.discord.json.discord.CheckSuite
import at.hannibal2.skyhanni.discord.json.discord.Conclusion
import at.hannibal2.skyhanni.discord.json.discord.Deployment
import at.hannibal2.skyhanni.discord.json.discord.Output
import at.hannibal2.skyhanni.discord.json.discord.RunStatus
import at.hannibal2.skyhanni.discord.json.discord.WorkflowRun
import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubClientTest {
    private val github = GitHubClient("SkyHanniStudios", "SkyHanni", "")

    @Test
    fun `selects latest matrix check run`() {
        val selected = github.selectCheckRun(
            listOf(
                checkRun(
                    id = 10,
                    runId = 100,
                    name = "Build and test (1.21.11)",
                    status = RunStatus.COMPLETED,
                    conclusion = Conclusion.CANCELLED,
                ),
                checkRun(
                    id = 20,
                    runId = 200,
                    name = "Build and test (1.21.11)",
                    status = RunStatus.IN_PROGRESS,
                    conclusion = null,
                ),
                checkRun(
                    id = 21,
                    runId = 200,
                    name = "Build and test (26.1)",
                    status = RunStatus.IN_PROGRESS,
                    conclusion = null,
                ),
            ),
            "Build and test",
        )

        assertEquals(21, selected?.id)
    }

    @Test
    fun `selects failed matrix check over successful check in the same run`() {
        val selected = github.selectCheckRun(
            listOf(
                checkRun(
                    id = 30,
                    runId = 300,
                    name = "Build and test (1.21.11)",
                    conclusion = Conclusion.SUCCESS,
                ),
                checkRun(
                    id = 31,
                    runId = 300,
                    name = "Build and test (26.1)",
                    conclusion = Conclusion.FAILURE,
                ),
            ),
            "Build and test",
        )

        assertEquals(31, selected?.id)
    }

    @Test
    fun `detects workflow runs needing approval from status`() {
        assertEquals(
            true,
            github.hasWorkflowRunNeedingApproval(
                listOf(workflowRun(status = "action_required", conclusion = null)),
            ),
        )
    }

    @Test
    fun `detects workflow runs needing approval from conclusion`() {
        assertEquals(
            true,
            github.hasWorkflowRunNeedingApproval(
                listOf(workflowRun(status = "completed", conclusion = "action_required")),
            ),
        )
    }

    @Test
    fun `does not require approval when workflow runs have no action required state`() {
        assertEquals(
            false,
            github.hasWorkflowRunNeedingApproval(
                listOf(workflowRun(status = "completed", conclusion = "success")),
            ),
        )
    }

    private fun checkRun(
        id: Long,
        runId: Long,
        name: String,
        status: RunStatus = RunStatus.COMPLETED,
        conclusion: Conclusion? = Conclusion.SUCCESS,
    ): CheckRun {
        return CheckRun(
            id = id,
            headSha = "head",
            nodeId = "node-$id",
            externalId = null,
            url = "https://api.github.com/check-runs/$id",
            htmlUrl = "https://github.com/SkyHanniStudios/SkyHanni/actions/runs/$runId/job/$id",
            detailsUrl = null,
            status = status,
            conclusion = conclusion,
            startedAt = "2026-06-13T23:40:53Z",
            completedAt = if (status == RunStatus.COMPLETED) "2026-06-13T23:45:53Z" else null,
            output = Output(
                title = null,
                summary = null,
                text = null,
                annotationsCount = 0,
                annotationsUrl = "https://api.github.com/check-runs/$id/annotations",
            ),
            name = name,
            checkSuite = CheckSuite(runId),
            app = null,
            pullRequests = emptyList(),
            deployment = Deployment(
                url = "https://api.github.com/deployments/$id",
                id = id,
                nodeId = "deployment-$id",
                task = "deploy",
                originalEnvironment = "ci",
                environment = "ci",
                description = null,
                createdAt = "2026-06-13T23:40:53Z",
                updatedAt = "2026-06-13T23:40:53Z",
                statusesUrl = "https://api.github.com/deployments/$id/statuses",
                repositoryUrl = "https://api.github.com/repos/SkyHanniStudios/SkyHanni",
                transientEnvironment = false,
                productionEnvironment = false,
                performedViaGitHubApp = null,
            ),
        )
    }

    private fun workflowRun(
        status: String?,
        conclusion: String?,
    ): WorkflowRun {
        return WorkflowRun(
            id = 1,
            name = "Build",
            headSha = "head",
            status = status,
            conclusion = conclusion,
        )
    }

    @Test
    fun `counts approvals correctly with latest review overriding previous`() {
        val approvals = github.calculateApprovals(
            listOf(
                mapOf(
                    "user" to mapOf("login" to "user1"),
                    "state" to "APPROVED",
                    "submitted_at" to "2026-06-01T10:00:00Z"
                ),
                mapOf(
                    "user" to mapOf("login" to "user1"),
                    "state" to "CHANGES_REQUESTED",
                    "submitted_at" to "2026-06-01T11:00:00Z"
                ), // user1 latest is CHANGES_REQUESTED -> not approved
                mapOf(
                    "user" to mapOf("login" to "user2"),
                    "state" to "CHANGES_REQUESTED",
                    "submitted_at" to "2026-06-01T10:00:00Z"
                ),
                mapOf(
                    "user" to mapOf("login" to "user2"),
                    "state" to "APPROVED",
                    "submitted_at" to "2026-06-01T12:00:00Z"
                ), // user2 latest is APPROVED -> counts as 1 approval
                mapOf(
                    "user" to mapOf("login" to "user3"),
                    "state" to "APPROVED",
                    "submitted_at" to "2026-06-01T09:00:00Z"
                ) // user3 latest is APPROVED -> counts as 1 approval
            )
        )

        assertEquals(2, approvals)
    }

    @Test
    fun `returns zero approvals when no reviews are approved`() {
        val approvals = github.calculateApprovals(
            listOf(
                mapOf(
                    "user" to mapOf("login" to "user1"),
                    "state" to "COMMENTED",
                    "submitted_at" to "2026-06-01T10:00:00Z"
                )
            )
        )

        assertEquals(0, approvals)
    }
}
