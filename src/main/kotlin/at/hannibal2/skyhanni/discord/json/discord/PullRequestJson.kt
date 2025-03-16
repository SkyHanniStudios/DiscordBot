package at.hannibal2.skyhanni.discord.json.discord

import com.google.gson.annotations.SerializedName

data class PullRequestJson (
    @SerializedName("url") val url: String,
    @SerializedName("id") val id: Long,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("diff_url") val diffUrl: String,
    @SerializedName("patch_url") val patchUrl: String,
    @SerializedName("issue_url") val issueUrl: String,
    @SerializedName("commits_url") val commitsUrl: String,
    @SerializedName("review_comments_url") val reviewCommentsUrl: String,
    @SerializedName("review_comment_url") val reviewCommentUrl: String,
    @SerializedName("comments_url") val commentsUrl: String,
    @SerializedName("statuses_url") val statusesUrl: String,
    @SerializedName("number") val number: Long,
    @SerializedName("state") val state: State,
    @SerializedName("locked") val locked: Boolean,
    @SerializedName("title") val title: String,
    @SerializedName("user") val user: SimpleUser,
    @SerializedName("body") val body: String?,
    @SerializedName("labels") val labels: List<Label>,
    @SerializedName("milestone") val milestone: Milestone?,
    @SerializedName("active_lock_reason") val activeLockReason: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("closed_at") val closedAt: String?,
    @SerializedName("merged_at") val mergedAt: String?,
    @SerializedName("merge_commit_sha") val mergeCommitSha: String?,
    @SerializedName("assignee") val assignee: SimpleUser?,
    @SerializedName("assignees") val assignees: List<SimpleUser>?,
    @SerializedName("requested_reviewers") val requestedReviewers: List<SimpleUser>?,
    @SerializedName("requested_teams") val requestedTeams: List<TeamSimple>?,
    @SerializedName("head") val head: Head,
    @SerializedName("base") val base: Base,
    @SerializedName("_links") val Links: Links,
    @SerializedName("author_association") val authorAssociation: AuthorAssociation,
    @SerializedName("auto_merge") val autoMerge: Automerge?,
    @SerializedName("draft") val draft: Boolean,
    @SerializedName("merged") val merged: Boolean,
    @SerializedName("mergeable") val mergeable: Boolean?,
    @SerializedName("rebaseable") val rebaseable: Boolean?,
    @SerializedName("mergeable_state") val mergeableState: String,
    @SerializedName("merged_by") val mergedBy: SimpleUser?,
    @SerializedName("comments") val comments: Long,
    @SerializedName("review_comments") val reviewComments: Long,
    @SerializedName("maintainer_can_modify") val maintainerCanModify: Boolean,
    @SerializedName("commits") val commits: Long,
    @SerializedName("additions") val additions: Long,
    @SerializedName("deletions") val deletions: Long,
    @SerializedName("changed_files") val changedFiles: Long
)

enum class State {
    @SerializedName("open") OPEN,
    @SerializedName("closed") CLOSED
}

data class SimpleUser (
    @SerializedName("name") val name: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("login") val login: String,
    @SerializedName("id") val id: Long,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("gravatar_id") val gravatarId: String?,
    @SerializedName("url") val url: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("followers_url") val followersUrl: String,
    @SerializedName("following_url") val followingUrl: String,
    @SerializedName("gists_url") val gistsUrl: String,
    @SerializedName("starred_url") val starredUrl: String,
    @SerializedName("subscriptions_url") val subscriptionsUrl: String,
    @SerializedName("organizations_url") val organizationsUrl: String,
    @SerializedName("repos_url") val reposUrl: String,
    @SerializedName("events_url") val eventsUrl: String,
    @SerializedName("received_events_url") val receivedEventsUrl: String,
    @SerializedName("type") val type: String,
    @SerializedName("site_admin") val siteAdmin: Boolean,
    @SerializedName("starred_at") val starredAt: String,
    @SerializedName("user_view_type") val userViewType: String
)

data class Label (
    @SerializedName("id") val id: Long,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("url") val url: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("color") val color: String,
    @SerializedName("default") val default: Boolean
)

data class Milestone (
    @SerializedName("url") val url: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("labels_url") val labelsUrl: String,
    @SerializedName("id") val id: Long,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("number") val number: Long,
    @SerializedName("state") val state: MilestoneState,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("creator") val creator: SimpleUser?,
    @SerializedName("open_issues") val openIssues: Long,
    @SerializedName("closed_issues") val closedIssues: Long,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("closed_at") val closedAt: String?,
    @SerializedName("due_on") val dueOn: String?
)

enum class MilestoneState {
    @SerializedName("open") OPEN,
    @SerializedName("closed") CLOSED
}

data class TeamSimple (
    @SerializedName("id") val id: Long,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("url") val url: String,
    @SerializedName("members_url") val membersUrl: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("permission") val permission: String,
    @SerializedName("privacy") val privacy: String,
    @SerializedName("notification_setting") val notificationSetting: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("repositories_url") val repositoriesUrl: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("ldap_dn") val ldapDn: String
)

data class Head (
    @SerializedName("label") val label: String,
    @SerializedName("ref") val ref: String,
    @SerializedName("repo") val repo: Repository,
    @SerializedName("sha") val sha: String,
    @SerializedName("user") val user: SimpleUser
)

data class Repository (
    @SerializedName("id") val id: Long,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("name") val name: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("license") val license: LicenseSimple?,
    @SerializedName("forks") val forks: Long,
    @SerializedName("permissions") val permissions: Permissions,
    @SerializedName("owner") val owner: SimpleUser,
    @SerializedName("private") val private: Boolean,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("description") val description: String?,
    @SerializedName("fork") val fork: Boolean,
    @SerializedName("url") val url: String,
    @SerializedName("archive_url") val archiveUrl: String,
    @SerializedName("assignees_url") val assigneesUrl: String,
    @SerializedName("blobs_url") val blobsUrl: String,
    @SerializedName("branches_url") val branchesUrl: String,
    @SerializedName("collaborators_url") val collaboratorsUrl: String,
    @SerializedName("comments_url") val commentsUrl: String,
    @SerializedName("commits_url") val commitsUrl: String,
    @SerializedName("compare_url") val compareUrl: String,
    @SerializedName("contents_url") val contentsUrl: String,
    @SerializedName("contributors_url") val contributorsUrl: String,
    @SerializedName("deployments_url") val deploymentsUrl: String,
    @SerializedName("downloads_url") val downloadsUrl: String,
    @SerializedName("events_url") val eventsUrl: String,
    @SerializedName("forks_url") val forksUrl: String,
    @SerializedName("git_commits_url") val gitCommitsUrl: String,
    @SerializedName("git_refs_url") val gitRefsUrl: String,
    @SerializedName("git_tags_url") val gitTagsUrl: String,
    @SerializedName("git_url") val gitUrl: String,
    @SerializedName("issue_comment_url") val issueCommentUrl: String,
    @SerializedName("issue_events_url") val issueEventsUrl: String,
    @SerializedName("issues_url") val issuesUrl: String,
    @SerializedName("keys_url") val keysUrl: String,
    @SerializedName("labels_url") val labelsUrl: String,
    @SerializedName("languages_url") val languagesUrl: String,
    @SerializedName("merges_url") val mergesUrl: String,
    @SerializedName("milestones_url") val milestonesUrl: String,
    @SerializedName("notifications_url") val notificationsUrl: String,
    @SerializedName("pulls_url") val pullsUrl: String,
    @SerializedName("releases_url") val releasesUrl: String,
    @SerializedName("ssh_url") val sshUrl: String,
    @SerializedName("stargazers_url") val stargazersUrl: String,
    @SerializedName("statuses_url") val statusesUrl: String,
    @SerializedName("subscribers_url") val subscribersUrl: String,
    @SerializedName("subscription_url") val subscriptionUrl: String,
    @SerializedName("tags_url") val tagsUrl: String,
    @SerializedName("teams_url") val teamsUrl: String,
    @SerializedName("trees_url") val treesUrl: String,
    @SerializedName("clone_url") val cloneUrl: String,
    @SerializedName("mirror_url") val mirrorUrl: String?,
    @SerializedName("hooks_url") val hooksUrl: String,
    @SerializedName("svn_url") val svnUrl: String,
    @SerializedName("homepage") val homepage: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("forks_count") val forksCount: Long,
    @SerializedName("stargazers_count") val stargazersCount: Long,
    @SerializedName("watchers_count") val watchersCount: Long,
    @SerializedName("size") val size: Long,
    @SerializedName("default_branch") val defaultBranch: String,
    @SerializedName("open_issues_count") val openIssuesCount: Long,
    @SerializedName("is_template") val isTemplate: Boolean,
    @SerializedName("topics") val topics: List<String>,
    @SerializedName("has_issues") val hasIssues: Boolean,
    @SerializedName("has_projects") val hasProjects: Boolean,
    @SerializedName("has_wiki") val hasWiki: Boolean,
    @SerializedName("has_pages") val hasPages: Boolean,
    @SerializedName("has_downloads") val hasDownloads: Boolean,
    @SerializedName("has_discussions") val hasDiscussions: Boolean,
    @SerializedName("archived") val archived: Boolean,
    @SerializedName("disabled") val disabled: Boolean,
    @SerializedName("visibility") val visibility: String,
    @SerializedName("pushed_at") val pushedAt: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("allow_rebase_merge") val allowRebaseMerge: Boolean,
    @SerializedName("temp_clone_token") val tempCloneToken: String,
    @SerializedName("allow_squash_merge") val allowSquashMerge: Boolean,
    @SerializedName("allow_auto_merge") val allowAutoMerge: Boolean,
    @SerializedName("delete_branch_on_merge") val deleteBranchOnMerge: Boolean,
    @SerializedName("allow_update_branch") val allowUpdateBranch: Boolean,
    @SerializedName("use_squash_pr_title_as_default") val useSquashPrTitleAsDefault: Boolean,
    @SerializedName("squash_merge_commit_title") val squashMergeCommitTitle: SquashMergeCommitTitle,
    @SerializedName("squash_merge_commit_message") val squashMergeCommitMessage: SquashMergeCommitMessage,
    @SerializedName("merge_commit_title") val mergeCommitTitle: MergeCommitTitle,
    @SerializedName("merge_commit_message") val mergeCommitMessage: MergeCommitMessage,
    @SerializedName("allow_merge_commit") val allowMergeCommit: Boolean,
    @SerializedName("allow_forking") val allowForking: Boolean,
    @SerializedName("web_commit_signoff_required") val webCommitSignoffRequired: Boolean,
    @SerializedName("open_issues") val openIssues: Long,
    @SerializedName("watchers") val watchers: Long,
    @SerializedName("master_branch") val masterBranch: String,
    @SerializedName("starred_at") val starredAt: String,
    @SerializedName("anonymous_access_enabled") val anonymousAccessEnabled: Boolean
)

data class LicenseSimple (
    @SerializedName("key") val key: String,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String?,
    @SerializedName("spdx_id") val spdxId: String?,
    @SerializedName("node_id") val nodeId: String,
    @SerializedName("html_url") val htmlUrl: String
)

data class Permissions (
    @SerializedName("admin") val admin: Boolean,
    @SerializedName("pull") val pull: Boolean,
    @SerializedName("triage") val triage: Boolean,
    @SerializedName("push") val push: Boolean,
    @SerializedName("maintain") val maintain: Boolean
)

enum class SquashMergeCommitTitle {
    @SerializedName("PR_TITLE") PR_TITLE,
    @SerializedName("COMMIT_OR_PR_TITLE") COMMIT_OR_PR_TITLE
}

enum class SquashMergeCommitMessage {
    @SerializedName("PR_BODY") PR_BODY,
    @SerializedName("COMMIT_MESSAGES") COMMIT_MESSAGES,
    @SerializedName("BLANK") BLANK
}

enum class MergeCommitTitle {
    @SerializedName("PR_TITLE") PR_TITLE,
    @SerializedName("MERGE_MESSAGE") MERGE_MESSAGE
}

enum class MergeCommitMessage {
    @SerializedName("PR_BODY") PR_BODY,
    @SerializedName("PR_TITLE") PR_TITLE,
    @SerializedName("BLANK") BLANK
}

data class Base (
    @SerializedName("label") val label: String,
    @SerializedName("ref") val ref: String,
    @SerializedName("repo") val repo: Repository,
    @SerializedName("sha") val sha: String,
    @SerializedName("user") val user: SimpleUser
)

data class Links (
    @SerializedName("comments") val comments: Link,
    @SerializedName("commits") val commits: Link,
    @SerializedName("statuses") val statuses: Link,
    @SerializedName("html") val html: Link,
    @SerializedName("issue") val issue: Link,
    @SerializedName("review_comments") val reviewComments: Link,
    @SerializedName("review_comment") val reviewComment: Link,
    @SerializedName("self") val self: Link
)

data class Link (
    @SerializedName("href") val href: String
)

enum class AuthorAssociation {
    @SerializedName("COLLABORATOR") COLLABORATOR,
    @SerializedName("CONTRIBUTOR") CONTRIBUTOR,
    @SerializedName("FIRST_TIMER") FIRST_TIMER,
    @SerializedName("FIRST_TIME_CONTRIBUTOR") FIRST_TIME_CONTRIBUTOR,
    @SerializedName("MANNEQUIN") MANNEQUIN,
    @SerializedName("MEMBER") MEMBER,
    @SerializedName("NONE") NONE,
    @SerializedName("OWNER") OWNER
}

data class Automerge (
    @SerializedName("enabled_by") val enabledBy: SimpleUser,
    @SerializedName("merge_method") val mergeMethod: MergeMethod,
    @SerializedName("commit_title") val commitTitle: String,
    @SerializedName("commit_message") val commitMessage: String
)

enum class MergeMethod {
    @SerializedName("merge") MERGE,
    @SerializedName("squash") SQUASH,
    @SerializedName("rebase") REBASE
}