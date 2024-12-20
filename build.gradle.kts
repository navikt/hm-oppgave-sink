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
    implementation(libs.kotlin.logging)
    implementation(libs.rapidsAndRivers)
    implementation(libs.hotlibs.http)
    implementation(libs.wiremock)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.jackson)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

spotless {
    kotlin {
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_enum-entry-name-case" to "disabled",
                "ktlint_standard_filename" to "disabled",
                "ktlint_standard_function-naming" to "disabled",
                "ktlint_standard_property-naming" to "disabled",
                "ktlint_standard_value-argument-comment" to "disabled",
                "ktlint_standard_value-parameter-comment" to "disabled",
            ),
        )
        targetExclude("build/generated/**/*")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useKotlinTest(libs.versions.kotlin.asProvider())
            dependencies {
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
                implementation(libs.tbdLibs.rapidsAndRivers.test)
            }
        }
    }
}

val openApiGenerated: Provider<Directory> = layout.buildDirectory.dir("generated/source/openapi")
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.file("src/main/resources/oppgave/openapi.yaml").toString())
    outputDir.set(openApiGenerated.map(Directory::toString))
    packageName.set("no.nav.hjelpemidler.oppgave.client")
    globalProperties.set(
        mapOf(
            "apis" to "none",
            "models" to "",
            "modelDocs" to "false",
            "modelTests" to "false",
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
            srcDir(openApiGenerated.map { it.dir("main") })
        }
    }
}

tasks {
    compileKotlin {
        dependsOn(openApiGenerate)
        dependsOn("spotlessApply")
        dependsOn("spotlessCheck")
    }
    shadowJar { mergeServiceFiles() }
}
