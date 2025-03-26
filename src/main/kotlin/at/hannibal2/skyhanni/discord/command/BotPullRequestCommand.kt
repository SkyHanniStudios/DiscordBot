package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.*
import at.hannibal2.skyhanni.discord.github.GitHubClient
import at.hannibal2.skyhanni.discord.json.discord.PullRequestJson

object BotPullRequestCommand : PullRequestCommand() {
    override val repo get() = "DiscordBot"
    override val user get() = "SkyHanniStudios"
    override var disableBuildInfo: Boolean = true
    override val github get() = GitHubClient(user, repo, BOT.config.githubToken)

    override val labelTypes: Map<String, Set<String>> get() = mapOf(
        Pair("Misc", setOf("Bug Fix", "Merge Conflicts", "Waiting on Dependency PR"))
    )

    override fun StringBuilder.appendLabelCategories(labels: Set<String>, pr: PullRequestJson) {
        appendLabelCategory("Misc", labels, this)
    }

    override val name: String = "botpr"
    override val description: String = "Displays useful information about a pull request to the bot on Github."
    override val options: List<Option> = listOf(
        Option("number", "Number of the bot pull request you want to display.")
    )
}
