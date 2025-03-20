package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.*

object RepoPullRequestCommand : PullRequestCommand() {
    override val repo get() = "SkyHanni-REPO"
    override var disableBuildInfo: Boolean = true

    override val name: String = "repopr"
    override val description: String = "Displays useful information about a repo pull request on Github."
    override val options: List<Option> = listOf(
        Option("number", "Number of the repo pull request you want to display.")
    )
}
