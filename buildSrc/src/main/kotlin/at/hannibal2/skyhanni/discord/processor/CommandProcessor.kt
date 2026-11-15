package at.hannibal2.skyhanni.discord.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

private const val COMMAND_PACKAGE = "at.hannibal2.skyhanni.discord.command"
private const val SLASH_COMMAND = "$COMMAND_PACKAGE.SkyHanniSlashCommand"
private const val BASE_COMMAND = "$COMMAND_PACKAGE.BaseCommand"
private const val LOADED_COMMANDS = "LoadedCommands"

class CommandProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        generated = true

        val commands = resolver.getSymbolsWithAnnotation(SLASH_COMMAND)
            .mapNotNull { it.asCommand() }
            .sortedBy { it.qualifiedName?.asString() }
            .toList()
        logger.info("Found ${commands.size} classes with @SkyHanniSlashCommand")

        val sources = commands.mapNotNull { it.containingFile }.toTypedArray()
        codeGenerator.createNewFile(Dependencies(true, *sources), COMMAND_PACKAGE, LOADED_COMMANDS).writer().use {
            it.write(commands.toSource())
        }
        return emptyList()
    }

    private fun KSAnnotated.asCommand(): KSClassDeclaration? {
        val declaration = this as? KSClassDeclaration
        val isCommand = declaration?.getAllSuperTypes()
            ?.any { it.declaration.qualifiedName?.asString() == BASE_COMMAND } == true
        if (!isCommand) logger.error("@SkyHanniSlashCommand is only valid on classes that extend BaseCommand", this)
        return declaration?.takeIf { isCommand }
    }

    private fun List<KSClassDeclaration>.toSource(): String = buildString {
        appendLine("package $COMMAND_PACKAGE")
        appendLine()
        appendLine("object $LOADED_COMMANDS {")
        appendLine("    val factories: List<() -> BaseCommand> = listOf(")
        for (command in this@toSource) {
            val name = command.qualifiedName?.asString() ?: continue
            val instance = if (command.classKind == ClassKind.OBJECT) name else "$name()"
            appendLine("        { $instance },")
        }
        appendLine("    )")
        appendLine("}")
    }
}
