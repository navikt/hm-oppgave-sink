plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.openapi)
    alias(libs.plugins.spotless)
}

application {
    applicationName = "hm-oppgave-sink"
    mainClass.set("no.nav.hjelpemidler.oppgave.ApplicationKt")
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.logging)
    implementation(libs.rapidsAndRivers)
    implementation(libs.hm.http)

    // fixme -> bytt med MockEngine
    implementation("com.github.tomakehurst:wiremock:3.0.1")

    // Test
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
}

kotlin {
    jvmToolchain(17)
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

tasks.compileKotlin {
    dependsOn(tasks.openApiGenerate)
}

tasks.test {
    useJUnitPlatform()
}

openApiGenerate {
    inputSpec.set("$rootDir/src/main/resources/oppgave/openapi.yaml")
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
