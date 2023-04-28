import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.8.20"
    id("com.expediagroup.graphql") version "6.4.0"
    id("org.openapi.generator") version "6.5.0"
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

    // Http
    implementation("no.nav.hjelpemidler.http:hm-http:v0.0.29")

    // Logging
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    // GraphQL
    val graphQLVersion = "6.4.0"
    implementation("com.expediagroup:graphql-kotlin-ktor-client:$graphQLVersion") {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
    }
    implementation("com.expediagroup:graphql-kotlin-client-jackson:$graphQLVersion")

    // fixme -> bytt med MockEngine
    implementation("com.github.tomakehurst:wiremock:2.27.2")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation("io.kotest:kotest-runner-junit5:5.5.5")
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
}

spotless {
    kotlin {
        ktlint()
        targetExclude("build/generated/**/*")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    dependsOn("openApiGenerate")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

graphql {
    client {
        schemaFile = file("src/main/resources/pdl/pdl-api-sdl.graphqls")
        queryFileDirectory = "src/main/resources/pdl"
        packageName = "no.nav.hjelpemidler.pdl"
    }
}

openApiGenerate {
    inputSpec.set("src/main/resources/oppgave/openapi.yaml")
    outputDir.set("$buildDir/generated/source/openapi")
    generatorName.set("kotlin")
    packageName.set("no.nav.hjelpemidler.oppgave.client")
    globalProperties.set(
        mapOf(
            "apis" to "none",
            "models" to "",
            "modelDocs" to "false",
        ),
    )
    configOptions.set(
        mapOf(
            "serializationLibrary" to "jackson",
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "main",
        ),
    )
}

sourceSets {
    main {
        kotlin {
            srcDir("$buildDir/generated/source/openapi/main")
        }
    }
}
