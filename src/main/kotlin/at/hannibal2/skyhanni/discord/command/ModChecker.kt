package at.hannibal2.skyhanni.discord.command

// taken and edited from https://github.com/PartlySaneStudios/partly-sane-skies/blob/main/src/main/kotlin/me/partlysanestudios/partlysaneskies/features/security/modschecker/ModChecker.kt

//
// Written by hannibal002 and Su386.
// See LICENSE for copyright and license notices.
//

import at.hannibal2.skyhanni.discord.BOT
import at.hannibal2.skyhanni.discord.PARTY_FACE
import at.hannibal2.skyhanni.discord.PLEADING_FACE
import at.hannibal2.skyhanni.discord.Utils
import at.hannibal2.skyhanni.discord.Utils.getLink
import at.hannibal2.skyhanni.discord.Utils.getLinkName
import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.github.GitHubClient
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
    class UpdateModListCommand : BaseCommand() {
        override val name: String = "modlistupdate"
        override val description: String = "Updates the server list."
        override val aliases: List<String> = listOf("updatemodlist", "updatemods")

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
        val modToRemove = mutableListOf<String>()
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

            if (fileName.contains("forge-1.8.9-11.15.1.2318-1.8.9")) {
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
            val reasonNotToUse = latestFullMod.reasonNotToUse ?: latestBetaMod.reasonNotToUse
            reasonNotToUse?.let {
                modToRemove.add("- $name ($it)")
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

            if (name == "Velox Caelo" && version == "1.0.2" && latestVersion == "1.1.0") {
                ignored.add("velox wrong version format: $line")
                continue
            }

            val link = "Download".linkTo(latestBetaMod.downloadLink)
            updateAvaliable.add("$name (current: $version, latest: $latestVersion) - $link")

            result.add("- $name ($version -> $latestVersion) $link")
        }

        val debugList = mutableListOf<String>()
        fun debug(text: String) {
            if (debug) {
                debugList.add(text)
            }
        }

        debug("found mods in total: ${activeMods.size}")

        val errorMods =
            activeMods.size - unknownMod.size - upToDate.size - unknownVersion.size - updateAvaliable.size - ignored.size - modToRemove.size

        if (errorMods != 0) {
            if (debug) {
                debug("errorMods = $errorMods")
            } else {
                val a = "Wrong mod size from ${author.getLinkName()} at ${message.getLink()}"
                val b = "reply to the message with `!debugmods` to investigate!"
                Utils.sendMessageToBotChannel("$a\n$b")
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

        debug(" ")
        debug("${modToRemove.size} to remove mods:")
        for (line in modToRemove) {
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

        val toRemoveText = if (modToRemove.isNotEmpty()) {
            buildList {
                val label = if (modToRemove.size == 1) {
                    "this mod"
                } else "the following ${modToRemove.size} mods"
                add("### Please remove $label:")
                addAll(modToRemove)
            }.joinToString("\n")
        } else ""
        if (result.isEmpty()) {
            if (toRemoveText.isEmpty()) {
                reply("No outdated mods found $PARTY_FACE")
            } else {
                reply(toRemoveText)
            }
        } else {
            val label = if (result.size == 1) {
                "Found one outdated mod"
            } else "Found ${result.size} outdated mods"
            val resultAsText = result.joinToString("\n")
            reply("### $label $PLEADING_FACE\n$resultAsText\n$toRemoveText")
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

    private fun loadModDataFromRepo() {
        val json = github.getFileContent("data/mods.json") ?: error("Error loading mods json data")
//        val json = Utils.readStringFromClipboard() ?: error("error loading mods json from clipboard")

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
            val reasonNotToUse = modInfo.reasonNotToUse
            for ((version, hash) in versions) {
                val a = KnownMod(modId, modInfo.name, version, download, hash, false, reasonNotToUse)
                latest = a
                list.add(a)
            }
            for ((version, hash) in betaVersions) {
                val a = KnownMod(modId, modInfo.name, version, download, hash, true, reasonNotToUse)
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
