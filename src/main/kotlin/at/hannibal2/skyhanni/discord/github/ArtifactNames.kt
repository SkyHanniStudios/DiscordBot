package at.hannibal2.skyhanni.discord.github

object ArtifactNames {
    private val skyHanniJarArtifactPattern = Regex("""^SkyHanni-.+-mc(\d+(?:\.\d+)+)\.jar$""")

    fun isSkyHanniJarArtifact(name: String): Boolean = skyHanniJarArtifactPattern.matches(name)

    fun minecraftVersion(name: String): String? =
        skyHanniJarArtifactPattern.matchEntire(name)?.groupValues?.get(1)
}
