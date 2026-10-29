package at.hannibal2.skyhanni.discord.changelog

import at.hannibal2.changelog.ModVersion
import at.hannibal2.changelog.SkyHanniChangelogBuilder
import at.hannibal2.changelog.WhatToFetch

/**
 * Started by [ChangelogGenerator] in its own JVM, because the builder never clears its caches.
 * Touches nothing of the bot.
 */
object ChangelogRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size in 1..2) { "Expected <version> [previous version], got ${args.toList()}" }
        val version = ModVersion.fromString(args[0])
        val previousVersion = args.getOrNull(1)?.let { ModVersion.fromString(it) }
        SkyHanniChangelogBuilder.generateChangelog(WhatToFetch.ALREADY_MERGED, version, previousVersion)
    }
}
