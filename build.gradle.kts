plugins {
  kotlin("jvm") version "2.0.21"
  `maven-publish`
}

group = "com.c0x12c"
version = "0.1.0"

repositories {
  mavenCentral()
}

val arrowVersion = "1.2.4"
val jacksonVersion = "2.17.2"
val coroutinesVersion = "1.9.0"

dependencies {
  // Arrow for Either error handling
  api("io.arrow-kt:arrow-core:$arrowVersion")

  // Kotlin coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

  // Jackson for JSON
  implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

  // Logging
  implementation("org.slf4j:slf4j-api:2.0.16")

  // Test
  testImplementation(kotlin("test"))
  testImplementation("io.mockk:mockk:1.13.12")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
  testRuntimeOnly("ch.qos.logback:logback-classic:1.5.12")
}

kotlin {
  jvmToolchain(21)
}

tasks.test {
  useJUnitPlatform()
}

java {
  withSourcesJar()
  withJavadocJar()
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
  }
}
