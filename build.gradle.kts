plugins {
    application
    kotlin("jvm") version "2.0.0"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("net.dv8tion:JDA:5.3.0") // JDA Library
    implementation("org.xerial:sqlite-jdbc:3.49.1.0") // SQLite Driver
    implementation("com.google.code.gson:gson:2.12.1") // Gson

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("at.hannibal2.skyhanni.discord.DiscordBotKt")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "at.hannibal2.skyhanni.discord.DiscordBotKt")
    }
}
