package at.hannibal2.skyhanni.discord.handlers

import at.hannibal2.skyhanni.discord.Command
import java.io.File

object Commands {

    val commands: MutableMap<String, Command> = mutableMapOf()

    fun registerCommands() {
        val directoryPath = "src/main/kotlin/at/hannibal2/skyhanni/discord/commands"
        val baseClass = Command::class.java

        val dirs = File(directoryPath).listFiles { file -> file.isDirectory } ?: return
        dirs.forEach { dir ->
            val files = dir.listFiles { file -> file.extension == "kt" } ?: return
            files.forEach { file ->
                try {
                    val className = convertPathToClassName(file)
                    val clazz = Class.forName(className)

                    if (baseClass.isAssignableFrom(clazz) && clazz != baseClass) {
                        val commandInstance = clazz.getDeclaredConstructor().newInstance() as Command
                        val commandName = file.nameWithoutExtension.lowercase()

                        commands[commandName] = commandInstance
                    }
                } catch (e: ClassNotFoundException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun convertPathToClassName(file: File): String {
        // Get the absolute path and convert to package-style format
        val path = file.absolutePath
            .replace(File.separatorChar, '.')             // Convert file separators to dots
            .substringAfter("at")                         // Keep everything starting from 'at'
            .removeSuffix(".kt")                          // Remove the .kt extension

        return "at$path" // Prepend 'at.' to ensure it starts with 'at'
    }
}