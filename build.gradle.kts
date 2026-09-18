plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.openapi)
    alias(libs.plugins.spotless)
}

application {
    applicationName = "hm-oppgave-sink"
    mainClass = "no.nav.hjelpemidler.oppgave.sink.ApplicationKt"
}

dependencies {
    // hotlibs
    implementation(platform(libs.hotlibs.platform))
    implementation(libs.hotlibs.core)
    implementation(libs.hotlibs.http)
    implementation(libs.hotlibs.logging)
    implementation(libs.hotlibs.rapidsAndRivers)

    implementation(libs.wiremock)

    // Ktor
    implementation(libs.ktor.serialization.jackson3)
    implementation(libs.ktor.server.content.negotiation)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

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

testing {
    suites {
        @Suppress("UnstableApiUsage")
        val test =
            named<JvmTestSuite>("test") {
                useJUnitJupiter(libs.versions.junit)
                dependencies {
                    implementation(libs.hotlibs.test)
                    implementation(libs.rapidsAndRivers.test)
                }
            }
    }
}

val openApiGenerated: Provider<Directory> = layout.buildDirectory.dir("generated/source/openapi")
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.file("src/main/resources/oppgave/openapi.yaml"))
    outputDir.set(openApiGenerated)
    packageName.set("no.nav.hjelpemidler.oppgave.sink.client")
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
    shadowJar {
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
