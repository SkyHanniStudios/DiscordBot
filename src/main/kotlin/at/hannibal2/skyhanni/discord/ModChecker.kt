package at.hannibal2.skyhanni.discord

// taken and edited from https://github.com/PartlySaneStudios/partly-sane-skies/blob/main/src/main/kotlin/me/partlysanestudios/partlysaneskies/features/security/modschecker/ModChecker.kt

//
// Written by hannibal002 and Su386.
// See LICENSE for copyright and license notices.
//

import at.hannibal2.skyhanni.discord.Utils.getLink
import at.hannibal2.skyhanni.discord.Utils.getLinkName
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.command.BaseCommand
import at.hannibal2.skyhanni.discord.github.GitHubClient
import com.google.gson.Gson
import com.google.gson.annotations.Expose
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object ModChecker {
    private var knownMods = listOf<KnownMod>()

    // https://regex101.com/r/0W4NCa/1
    private val pattern = "\\[(?<modName>.*)]\\[(?<fileName>.*) \\((?<version>.*)\\)]".toPattern()

    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", BOT.config.githubTokenOwn)

    class ModInfo(val name: String, val version: String, val fileName: String)

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
    class UpdateModList : BaseCommand() {
        override val name: String = "modlistupdate"
        override val description: String = "Updates the server list."
        override val aliases: List<String> = listOf("updatemodlist")

        override fun MessageReceivedEvent.execute(args: List<String>) {
            reply("updating mod list ...")

            loadModDataFromRepo()
            val link = "GitHub".linkTo("https://github.com/SkyHanniStudios/DiscordBot/blob/master/data/mods.json")
            reply("Updated mod list from $link.")
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

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun MessageReceivedEvent.run(activeMods: Map<ModInfo, String>, debug: Boolean) {

        if (knownMods.isEmpty()) {
            loadModDataFromRepo()
        }

        if (knownMods.isEmpty()) error("known mods is empty")

        val unknownMod = mutableListOf<String>()
        val unknownVersion = mutableListOf<String>()
        val upToDate = mutableListOf<String>()
        val updateAvaliable = mutableListOf<String>()

        val result = mutableListOf<String>()

        val ignored = mutableListOf<String>()

        @Suppress("LoopWithTooManyJumpStatements") for ((mod, line) in activeMods) {
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
                ignored.add(line)
                continue
            }

            val forge1 = "forge-1.8.9-11.15.1.2318-1.8.9.jar"
            val forge2 = "forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"
            if (fileName == forge1 || fileName == forge2) {
                ignored.add(line)
                continue
            }

            if (name == "Forge Mod Loader") {
                unknownMod.add("unkown forge version: '$fileName'")
                continue
            }

            // has auto update, and too many small updates all the time, so no one cares
            if (name == "Essential") {
                ignored.add(line)
                continue
            }
            if (name == "OneConfig") {
                ignored.add(line)
                continue
            }

            // comes bundelled with other mods
            if (name == "Hypixel Mod API") {
                ignored.add(line)
                continue
            }

            // dulkir version bug
            if (name == "Dulkir Mod" && version == "\${version}") {
                ignored.add(line)
                continue
            }

            // spark version bug
            if (name == "spark" && version == "\${pluginVersion}") {
                ignored.add(line)
                continue
            }

            val latestFullMod = findModFromName(name)
            val latestBetaMod = findBetaFromName(name)

            if (latestFullMod == null || latestBetaMod == null) {
                unknownMod.add(line)
                continue
            }

            if (latestFullMod.version == version || latestBetaMod.version == version) {
                upToDate.add(name)
                continue
            }
            val currentVersion = knownMods.firstOrNull { it.name == mod.name && it.version == version }
            if (currentVersion == null) {
                val link = "Download".linkTo(latestBetaMod.downloadLink)
                unknownVersion.add("$name ($version) - $link")
                continue
            }
            val latestVersion = if (latestBetaMod.beta) latestBetaMod.version else latestFullMod.version
            val link = "Download".linkTo(latestBetaMod.downloadLink)
            updateAvaliable.add("$name (current: $version, latest: $latestVersion) - $link")

            result.add("$name ($version -> $latestVersion) $link")
        }

        val debugList = mutableListOf<String>()
        fun debug(text: String) {
            if (debug) {
                debugList.add(text)
            } else {
                // TODO remove for production
                println(text)
            }
        }

        debug("found mods in total: ${activeMods.size}")

        val errorMods =
            activeMods.size - unknownMod.size - upToDate.size - unknownVersion.size - updateAvaliable.size - ignored.size

        if (errorMods != 0) {
            if (debug) {
                debug("errorMods = $errorMods")
            } else {
                Utils.sendMessageToBotChannel(
                    "Wrong mod size from ${author.getLinkName()} at ${message.getLink()}\n" +
                            "reply to the message via `!debugmods` to investigate!"
                )
                return
            }
        }

        val forSupportChannel = mutableListOf<String>()

        debug(" ")
        if (unknownMod.size > 0) {
            forSupportChannel.add("${unknownMod.size} unknown mods:")
            forSupportChannel.addAll(unknownMod)
            forSupportChannel.add(" ")
        }
        debug("${unknownMod.size} unknown mods:")
        for (line in unknownMod) {
            debug(line)
        }

        debug(" ")
        debug("${ignored.size} ignored mods:")
        for (line in ignored) {
            debug(line)
        }

        debug(" ")
        debug("${upToDate.size} up to date mods:")
        for (line in upToDate) {
            debug(line)
        }

        if (unknownVersion.size > 0) {
            forSupportChannel.add("${unknownVersion.size} unknown mod versions:")
            forSupportChannel.addAll(unknownVersion)
            forSupportChannel.add(" ")
        }
        debug(" ")
        debug("${unknownVersion.size} unknown mod versions:")
        for (line in unknownVersion) {
            debug(line)
        }

        debug(" ")

        debug("Update avaliable for ${updateAvaliable.size} mods:")
        for (line in updateAvaliable) {
            debug(line)
        }

        if (debug) {
            reply("debug data for ${activeMods.size} mods in that message:\n${debugList.joinToString("\n")}")
            return
        }

        if (result.isEmpty()) {
            reply("no outdated mods found $PARTY_FACE")
        } else {
            reply("Found ${result.size} outdated mods $PLEADING_FACE\n" + result.joinToString("\n"))
        }
        if (forSupportChannel.isNotEmpty()) {
            val text = buildList {
                add("Unknown mod data from ${author.getLinkName()} at ${message.getLink()}")
                addAll(forSupportChannel)
            }.joinToString("\n")
            Utils.sendMessageToBotChannel(text)
        }
    }

    class ModDataJson {
        @Expose
        val mods: Map<String, ModInfo>? = null

        class ModInfo {
            @Expose
            var name: String = ""

            @Expose
            val download: String = ""

            @Expose
            val versions: Map<String, String> = HashMap()

            @Expose
            val betaVersions: Map<String, String> = HashMap()
        }
    }

    private fun loadModDataFromRepo() {
        val json = github.getFileContent("data/mods.json") ?: error("Error loading mods")

        val gson = Gson()
        val modData = gson.fromJson(json, ModDataJson::class.java)
        knownMods = read(modData)
    }

    private fun read(modData: ModDataJson): List<KnownMod> {
        val list: MutableList<KnownMod> = ArrayList()
        for ((modId, modInfo) in modData.mods ?: return ArrayList()) {

            // id odclient is the real one
            if (modId == "OdinClient") continue
            val download = modInfo.download
            var latest: KnownMod? = null
            var latestBeta: KnownMod? = null
            val versions = modInfo.versions
            val betaVersions = modInfo.betaVersions
            for ((version, hash) in versions) {
                val a = KnownMod(modId, modInfo.name, version, download, hash, false)
                latest = a
                list.add(a)
            }
            for ((version, hash) in betaVersions) {
                val a = KnownMod(modId, modInfo.name, version, download, hash, true)
                latestBeta = a
                list.add(a)
            }
            latest?.latest = true
            latestBeta?.latest = true
        }
        return list
    }

    private fun findModFromName(name: String): KnownMod? {
        for (mod in knownMods) {
            if (mod.name == name) {
                if (mod.latest && !mod.beta) {
                    return mod
                }
            }
        }
        return null
    }

    private fun findBetaFromName(name: String): KnownMod? {
        for (mod in knownMods) {
            if (mod.name == name) {
                if (mod.latest && mod.beta) {
                    return mod
                }
            }
        }
        return null
    }

    internal class KnownMod(
        internal val id: String,
        internal val name: String,
        internal val version: String,
        internal val downloadLink: String,
        internal val hash: String,
        internal val beta: Boolean,
    ) {
        internal var latest = false
    }
}
