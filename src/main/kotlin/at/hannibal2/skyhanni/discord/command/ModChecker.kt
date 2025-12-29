package at.hannibal2.skyhanni.discord.command

import at.hannibal2.skyhanni.discord.*
import at.hannibal2.skyhanni.discord.Utils.getLink
import at.hannibal2.skyhanni.discord.Utils.getLinkName
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.github.GitHubClient
import at.hannibal2.skyhanni.discord.utils.LiveLog
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import kotlin.time.Duration.Companion.seconds

object ModChecker {
    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", BOT.config.githubTokenOwn)
    private val githubLink = "GitHub".linkTo("https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/mods.json")

    private var knownMods = listOf<KnownMod>()
    var isLoading = false
        private set

    // https://regex101.com/r/0W4NCa/1
    private val pattern = "\\[(?<modName>.*)]\\[(?<fileName>.*) \\((?<version>.*)\\)]".toPattern()

    class ModInfo(val name: String, val version: String, val fileName: String)

    fun loadModDataFromRepo() {
        isLoading = true
        Utils.runAsync("load mod data") {
            val log = LiveLog(
                Utils.getBotChannel(),
                "Mod List Update (via ${if (useClipboardInModChecker) "dev clipboard" else githubLink})"
            )
            log.startAutoUpdate()
            try {
                log.status("Loading from GitHub...")
                val json = if (useClipboardInModChecker) {
                    Utils.readStringFromClipboard() ?: error("error loading mods json from clipboard")
                } else {
                    github.getFileContent("data/mods.json") ?: error("Error loading mods json data")
                }

                log.log("Parsing JSON...")
                val modData = Gson().fromJson(json, ModDataJson::class.java)
                knownMods = read(modData)

                log.complete("${knownMods.size} mod versions loaded")
                BOT.logger.info("Loaded ${knownMods.size} mod versions from repo")
            } catch (e: Exception) {
                log.complete("Error: ${e.message}", status = "Failed")
                throw e
            } finally {
                isLoading = false
            }
        }
    }

    @Suppress("unused")
    class DebugModsCommand : BaseCommand() {
        override val name = "debugmods"
        override val description = "Debug infos about the mod list in neu stats format"

        override fun MessageReceivedEvent.execute(args: List<String>) {
            val referencedMessage = message.referencedMessage

            if (referencedMessage == null) {
                reply("reply to a message to see debug infos from that message!")
                return
            }
            val text = referencedMessage.contentRaw.trim()
            val mods = readModsFromMessage(text)
            if (mods == null) {
                reply("no mods found in that message!")
                return
            }
            run(mods, debug = true)
        }
    }

    @Suppress("unused")
    class UpdateModListCommand : BaseCommand() {
        override val name = "modlistupdate"
        override val description = "Updates the mod list."
        override val aliases = listOf("updatemodlist", "updatemods")

        init {
            Utils.runDelayed("init load mods", 1.seconds) {
                if (!isLoading) {
                    loadModDataFromRepo()
                }
            }
        }

        override fun MessageReceivedEvent.execute(args: List<String>) {
            if (isLoading) {
                reply("Mod list is already updating!")
                return
            }
            loadModDataFromRepo()
        }
    }

    fun isModList(event: MessageReceivedEvent, message: String): Boolean {
        val mods = readModsFromMessage(message) ?: return false
        event.run(mods, debug = false)
        return true
    }

    @Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
    private fun readModsFromMessage(message: String): MutableMap<ModInfo, String>? {
        val lines = message.split("\n")
        val startLine = "# Mods Loaded"
        if (!lines.any { it == startLine }) return null

        val mods = mutableMapOf<ModInfo, String>()
        var active = false
        for (line in lines) {
            if (line == startLine) {
                active = true
                continue
            }
            if (active) {
                val mod = readModInfo(line) ?: break
                mods[mod] = line
            }
        }
        if (mods.isEmpty()) return null
        return mods
    }

    private fun readModInfo(line: String): ModInfo? {
        val matcher = pattern.matcher(line)
        if (!matcher.matches()) return null

        val name = matcher.group("modName")
        val fileName = matcher.group("fileName")
        val version = matcher.group("version")
        return ModInfo(name, version, fileName)
    }

    enum class ModCategory(val label: String, val notifySupport: Boolean = false) {
        UNKNOWN_MOD("unknown mods", notifySupport = true),
        UNKNOWN_VERSION("unknown mod versions", notifySupport = true),
        UP_TO_DATE("up to date mods"),
        TO_REMOVE("to remove mods"),
        UPDATE_AVAILABLE("update available"),
        RESULT("result"),
        IGNORED("ignored mods")
    }

    // Mods to ignore during version checking
    private val ignoredMods = setOf(
        "Essential",      // has auto update, too many small updates
        "OneConfig",
        "Hypixel Mod API" // comes bundled with other mods
    )

    private fun analyzeMods(activeMods: Map<ModInfo, String>): Map<ModCategory, MutableList<String>> {
        val categories = ModCategory.entries.associateWith { mutableListOf<String>() }

        for ((mod, line) in activeMods) {
            val name = when (mod.name) {
                // odin/odin client is weird/mixed up in the json file
                "Odin" -> "OdinClient"
                // not so essential is fancy
                "§cNot §aSo §9Essential" -> "Not So Essential"
                else -> mod.name
            }

            val fileName = mod.fileName
            val version = mod.version

            // hide minecraft/forge/mod loader stuff
            if (fileName == "minecraft.jar") {
                categories[ModCategory.IGNORED]!!.add(line)
                continue
            }

            if (fileName.contains("forge-1.8.9-11.15.1.2318-1.8.9")) {
                categories[ModCategory.IGNORED]!!.add(line)
                continue
            }

            if (name == "Forge Mod Loader") {
                categories[ModCategory.UNKNOWN_MOD]!!.add("unknown forge version: '$fileName'")
                continue
            }

            if (name in ignoredMods) {
                categories[ModCategory.IGNORED]!!.add(line)
                continue
            }

            // dulkir version bug
            if (name == "Dulkir Mod" && version == "\${version}") {
                categories[ModCategory.IGNORED]!!.add(line)
                continue
            }

            // spark version bug
            if (name == "spark" && version == "\${pluginVersion}") {
                categories[ModCategory.IGNORED]!!.add(line)
                continue
            }

            val latestFullMod = findMod(name, beta = false)
            val latestBetaMod = findMod(name, beta = true)

            if (latestFullMod == null || latestBetaMod == null) {
                categories[ModCategory.UNKNOWN_MOD]!!.add(line)
                continue
            }

            val reasonNotToUse = latestFullMod.reasonNotToUse ?: latestBetaMod.reasonNotToUse
            reasonNotToUse?.let {
                categories[ModCategory.TO_REMOVE]!!.add("- $name ($it)")
                return@let
            }
            if (reasonNotToUse != null) continue

            if (latestFullMod.version == version || latestBetaMod.version == version) {
                categories[ModCategory.UP_TO_DATE]!!.add(name)
                continue
            }

            val currentVersion = knownMods.firstOrNull { it.name == mod.name && it.version == version }
            if (currentVersion == null) {
                val link = "Download".linkTo(latestBetaMod.downloadLink)
                categories[ModCategory.UNKNOWN_VERSION]!!.add("$name ($version) - $link")
                continue
            }

            val latestVersion = if (latestBetaMod.beta) latestBetaMod.version else latestFullMod.version

            if (name == "Velox Caelo" && version == "1.0.2" && latestVersion == "1.1.0") {
                categories[ModCategory.IGNORED]!!.add("velox wrong version format: $line")
                continue
            }

            val link = "Download".linkTo(latestBetaMod.downloadLink)
            categories[ModCategory.UPDATE_AVAILABLE]!!.add("$name (current: $version, latest: $latestVersion) - $link")
            categories[ModCategory.RESULT]!!.add("- $name ($version -> $latestVersion) $link")
        }

        return categories
    }

    private fun buildDebugOutput(
        activeMods: Map<ModInfo, String>,
        categories: Map<ModCategory, MutableList<String>>,
        errorMods: Int
    ): String {
        val lines = mutableListOf<String>()
        lines.add("found mods in total: ${activeMods.size}")

        if (errorMods != 0) {
            lines.add("errorMods = $errorMods")
        }

        ModCategory.entries.filter { it != ModCategory.RESULT }.forEach { category ->
            val items = categories[category]!!
            lines.add(" ")
            lines.add("${items.size} ${category.label}:")
            lines.addAll(items)
        }

        return "debug data for ${activeMods.size} mods in that message:\n${lines.joinToString("\n")}"
    }

    private fun MessageReceivedEvent.run(activeMods: Map<ModInfo, String>, debug: Boolean) {
        if (knownMods.isEmpty() && !isLoading) {
            loadModDataFromRepo()
        }
        if (knownMods.isEmpty()) {
            reply("Mod list not loaded yet, please try again in a moment.")
            return
        }

        val categories = analyzeMods(activeMods)
        val errorMods = activeMods.size - categories.values.sumOf { it.size }

        if (errorMods != 0 && !debug) {
            val a = "Wrong mod size from ${author.getLinkName()} at ${message.getLink()}"
            val b = "reply to the message with `!debugmods` to investigate!"
            Utils.sendMessageToBotChannel("$a\n$b")
            return
        }

        if (debug) {
            reply(buildDebugOutput(activeMods, categories, errorMods))
            return
        }

        reply(buildUserReply(categories[ModCategory.RESULT]!!, categories[ModCategory.TO_REMOVE]!!))
        notifySupportChannel(categories)
    }

    private fun MessageReceivedEvent.notifySupportChannel(categories: Map<ModCategory, MutableList<String>>) {
        val lines = mutableListOf<String>()
        ModCategory.entries.filter { it.notifySupport }.forEach { category ->
            val items = categories[category]!!
            if (items.isNotEmpty()) {
                lines.add("${items.size} ${category.label}:")
                lines.addAll(items)
                lines.add(" ")
            }
        }
        if (lines.isNotEmpty()) {
            val text =
                "Unknown mod data from ${author.getLinkName()} at ${message.getLink()}\n${lines.joinToString("\n")}"
            Utils.sendMessageToBotChannel(text)
        }
    }

    private fun buildUserReply(result: List<String>, modToRemove: List<String>): String {
        val toRemoveText = if (modToRemove.isNotEmpty()) {
            val label = if (modToRemove.size == 1) "this mod" else "the following ${modToRemove.size} mods"
            "### Please remove $label:\n${modToRemove.joinToString("\n")}"
        } else ""

        return when {
            result.isEmpty() && toRemoveText.isEmpty() -> "No outdated mods found $PARTY_FACE"
            result.isEmpty() -> toRemoveText
            else -> {
                val label = if (result.size == 1) "Found one outdated mod" else "Found ${result.size} outdated mods"
                "### $label $PLEADING_FACE\n${result.joinToString("\n")}\n$toRemoveText"
            }
        }
    }

    class ModDataJson {
        val mods: Map<String, ModInfo>? = null

        class ModInfo {
            var name: String = ""
            val download: String = ""

            @SerializedName("do_not_use_reason")
            val reasonNotToUse: String? = null
            val versions: Map<String, String> = HashMap()
            val betaVersions: Map<String, String> = HashMap()
        }
    }

    private fun read(modData: ModDataJson): List<KnownMod> = buildList {
        for ((modId, modInfo) in modData.mods ?: return emptyList()) {
            // id odclient is the real one
            if (modId == "OdinClient") continue
            with(modInfo) {
                var latest: KnownMod? = null
                var latestBeta: KnownMod? = null

                for ((version, hash) in versions) {
                    val mod = KnownMod(modId, name, version, download, hash, false, reasonNotToUse)
                    add(mod)
                    latest = mod
                }
                for ((version, hash) in betaVersions) {
                    val mod = KnownMod(modId, name, version, download, hash, true, reasonNotToUse)
                    add(mod)
                    latestBeta = mod
                }

                latest?.latest = true
                latestBeta?.latest = true
            }
        }
    }

    private fun findMod(name: String, beta: Boolean): KnownMod? =
        knownMods.find { it.name == name && it.latest && it.beta == beta }

    @Suppress("LongParameterList")
    internal class KnownMod(
        internal val id: String,
        internal val name: String,
        internal val version: String,
        internal val downloadLink: String,
        internal val hash: String,
        internal val beta: Boolean,
        internal val reasonNotToUse: String?,
    ) {
        internal var latest = false
    }
}
