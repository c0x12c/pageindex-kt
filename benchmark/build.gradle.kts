plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.c0x12c"
version = "0.1.0"

repositories {
    mavenCentral()
}

val jacksonVersion = "2.17.2"
val coroutinesVersion = "1.9.0"

dependencies {
    implementation(project(":"))

    // PDF parsing
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.c0x12c.pageindex.benchmark.MainKt")
}
