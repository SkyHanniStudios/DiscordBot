plugins {
    kotlin("jvm") version "2.0.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("net.dv8tion:JDA:5.0.0-beta.12") // JDA Library
    implementation("org.xerial:sqlite-jdbc:3.42.0.0") // SQLite Driver
    implementation("com.google.code.gson:gson:2.10.1") // Gson

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}