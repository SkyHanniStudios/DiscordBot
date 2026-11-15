plugins {
    `kotlin-dsl`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.google.devtools.ksp:symbol-processing-api:2.3.13")
}
