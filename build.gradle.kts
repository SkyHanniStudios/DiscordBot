import java.util.zip.ZipFile

plugins {
    application
    kotlin("jvm") version "2.1.0"
}

group = "at.hannibal.skyhannibot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("net.dv8tion:JDA:5.3.0") // JDA Library
    implementation("org.xerial:sqlite-jdbc:3.49.1.0") // SQLite Driver
    implementation("com.google.code.gson:gson:2.12.1") // Gson

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    // This could be replaced with another logging api to automatically generate log files
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")


    runtimeOnly("com.squareup.okhttp3:okhttp:4.3.1") // Http Client
    runtimeOnly("com.google.code.gson:gson:2.11.0") // Json
    implementation("org.reflections:reflections:0.10.2") // Reflections
    implementation("org.jetbrains.kotlin:kotlin-reflect") // Kotlin Reflection
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)

    // hides this error:
    // The feature "break continue in inline lambdas" is experimental and should be enabled explicitly
    compilerOptions {
        freeCompilerArgs.add("-Xnon-local-break-continue")
    }
}

application {
    mainClass.set("at.hannibal2.skyhanni.discord.DiscordBotKt")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "at.hannibal2.skyhanni.discord.DiscordBotKt")
    }
}

tasks.named("build").configure {
    dependsOn("extractBotJar")
}

tasks.register("extractBotJar") {
    val archiveFile = file("build/distributions/SHDiscordBot-1.0-SNAPSHOT.zip")
    val outputDir = layout.buildDirectory.dir("extractedBotJar").get().asFile
    doLast {
        val filePath = "SHDiscordBot-1.0-SNAPSHOT/lib/SHDiscordBot-1.0-SNAPSHOT.jar"
        ZipFile(archiveFile).use { zip ->
            val entry = zip.getEntry(filePath) ?: throw GradleException("$filePath not found in archive")
            outputDir.mkdirs()
            val outFile = File(outputDir, "SHDiscordBot-1.0-SNAPSHOT.jar")
            outFile.outputStream().use { os ->
                zip.getInputStream(entry).copyTo(os)
            }
            println("Extracted SHDiscordBot-1.0-SNAPSHOT.jar to ${outFile.absolutePath}")
        }
    }
}
