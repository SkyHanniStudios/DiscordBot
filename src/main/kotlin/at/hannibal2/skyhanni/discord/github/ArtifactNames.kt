package at.hannibal2.skyhanni.discord.github

object ArtifactNames {
    private val skyHanniJarPattern = Regex("""SkyHanni-.+-mc(?<version>\d+(?:\.\d+)+)\.jar""")

    fun isSkyHanniJar(name: String): Boolean = skyHanniJarPattern.matches(name)

    fun minecraftVersion(name: String): String? =
        skyHanniJarPattern.matchEntire(name)?.groups?.get("version")?.value
}