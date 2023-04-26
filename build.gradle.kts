import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.8.20"
    id("com.diffplug.spotless") version "6.18.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

application {
    applicationName = "hm-oppgave-sink"
    mainClass.set("no.nav.hjelpemidler.oppgave.ApplicationKt")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.navikt:rapids-and-rivers:2023041310341681374880.67ced5ad4dda")

    // Logging
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    // Fuel fixme -> bytt med hm-http
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")

    // fixme -> bytt med hm-http
    implementation("com.natpryce:konfig:1.6.10.0")

    // fixme -> bytt med MockEngine
    implementation("com.github.tomakehurst:wiremock:2.27.2")

    testImplementation(kotlin("test"))
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
