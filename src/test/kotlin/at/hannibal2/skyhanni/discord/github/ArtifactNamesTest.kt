package at.hannibal2.skyhanni.discord.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtifactNamesTest {
    @Test
    fun `matches versioned jar artifact names`() {
        assertTrue(ArtifactNames.isSkyHanniJar("SkyHanni-7.24.0-mc1.21.11.jar"))
        assertTrue(ArtifactNames.isSkyHanniJar("SkyHanni-7.24.0-mc26.1.jar"))
        assertFalse(ArtifactNames.isSkyHanniJar("SkyHanni-7.24.0-1.21.11.jar"))
        assertFalse(ArtifactNames.isSkyHanniJar("Test Results (1.21.11)"))
    }

    @Test
    fun `extracts minecraft version from jar artifact names`() {
        assertEquals("1.21.11", ArtifactNames.minecraftVersion("SkyHanni-7.24.0-mc1.21.11.jar"))
        assertEquals("26.1", ArtifactNames.minecraftVersion("SkyHanni-7.24.0-mc26.1.jar"))
        assertEquals(null, ArtifactNames.minecraftVersion("Test Results (26.1)"))
    }
}
