package at.hannibal2.skyhanni.discord

// taken and edited from https://github.com/PartlySaneStudios/partly-sane-skies/blob/main/src/main/kotlin/me/partlysanestudios/partlysaneskies/features/security/modschecker/ModChecker.kt

//
// Written by hannibal002 and Su386.
// See LICENSE for copyright and license notices.
//

import at.hannibal2.skyhanni.discord.Utils.linkTo
import at.hannibal2.skyhanni.discord.Utils.reply
import at.hannibal2.skyhanni.discord.github.GitHubClient
import com.google.gson.Gson
import com.google.gson.annotations.Expose
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object ModChecker {
    private var knownMods = listOf<KnownMod>()
    private val pattern = "\\[(?<modName>.*)]\\[(?<fileName>.*) \\((?<version>.*)\\)]".toPattern()
    private val github = GitHubClient("SkyHanniStudios", "DiscordBot", BOT.config.githubTokenOwn)

    class ModInfo(val name: String, val version: String, val fileName: String)

    @Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
    fun isModList(event: MessageReceivedEvent, message: String): Boolean {
        val lines = message.split("\n")
        val startLine = "# Mods Loaded"
        if (!lines.any { it == startLine }) return false

        val mods = mutableListOf<ModInfo>()
        var active = false
        for (line in lines) {
            if (line == startLine) {
                active = true
                continue
            }
            if (active) {
                val mod = readModInfo(line) ?: break
                mods.add(mod)
            }
        }

        if (mods.isEmpty()) return false

        event.run(mods)
        return true
    }

    private fun readModInfo(line: String): ModInfo? {
        //[Minecraft Coder Pack][minecraft.jar (9.19)]
        val matcher = pattern.matcher(line)
        if (!matcher.matches()) return null

        val name = matcher.group("modName")
        val fileName = matcher.group("fileName")
        val version = matcher.group("version")
        return ModInfo(name, version, fileName)

    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun MessageReceivedEvent.run(activeMods: List<ModInfo>) {
        println(" ")
        println(" ")
        println(" ")
        if (knownMods.isEmpty()) {
            loadModDataFromRepo()
        }


        if (knownMods.isEmpty()) error("known mods is empty")

        val unknownMod = mutableListOf<String>()
        val unknownVersion = mutableListOf<String>()
        val upToDate = mutableListOf<String>()
        val updateAvaliable = mutableListOf<String>()

        val result = mutableListOf<String>()

        @Suppress("LoopWithTooManyJumpStatements")
        for (mod in activeMods) {
            val rawName = mod.name
            // odin/odin client is weird/mixed up in the json file
            val name = if (rawName == "Odin") "OdinClient" else rawName

            val fileName = mod.fileName
            val version = mod.version

            // hide minecraft/forge/mod loader stuff
            if (fileName == "minecraft.jar") continue
            if (fileName == "forge-1.8.9-11.15.1.2318-1.8.9.jar") continue
            if (name == "Forge Mod Loader") {
                unknownMod.add("unkown forge version: '$fileName'")
                continue
            }

            // has auto update, and too many small updates all the time, so no one cares
            if (name == "Essential") continue
            if (name == "OneConfig") continue

            // comes bundelled with other mods
            if (name == "Hypixel Mod API") continue

            // dulkir version bug
            if (name == "Dulkir Mod" && version == "\${version}") continue

            val latestFullMod = findModFromName(name)
            val latestBetaMod = findBetaFromName(name)

            if (latestFullMod == null || latestBetaMod == null) {
                unknownMod.add("$name (${fileName})")
                continue
            }

            if (latestFullMod.version == version || latestBetaMod.version == version) {
                upToDate.add(name)
                continue
            }
            val currentVersion = knownMods.firstOrNull { it.name == mod.name && it.version == version }
            if (currentVersion == null) {
                val link = latestBetaMod.downloadLink
                unknownVersion.add("$name ($version) - $link")
                continue
            }
            val latestVersion = if (latestBetaMod.beta) latestBetaMod.version else latestFullMod.version
            val link = currentVersion.downloadLink

            updateAvaliable.add("$name (current: $version, latest: $latestVersion) - $$link")

            val downloadLink = "Download".linkTo(link)
            result.add("Update $name! ($version -> $latestVersion) $downloadLink")
        }

        println(" ")
        println("${unknownMod.size} unknown mods:")
        for (line in unknownMod) {
            println(line)
        }

        println(" ")
        println("${upToDate.size} up to date mods:")
        for (line in upToDate) {
            println(line)
        }

        println(" ")
        println("${unknownVersion.size} unknown mod versions:")
        for (line in unknownVersion) {
            println(line)
        }

        println(" ")

        println("Update avaliable for ${updateAvaliable.size} mods:")
        for (line in updateAvaliable) {
            println(line)
        }

        for (line in result) {
            println(line)
        }
        if (result.isEmpty()) {
            reply("no outdated mods found $PARTY_FACE")
        } else {
            reply("Found ${result.size} outdated mods $PLEADING_FACE\n" + result.joinToString("\n"))
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
