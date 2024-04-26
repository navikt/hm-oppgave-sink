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
    implementation(libs.wiremock)

    // Test
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
}

kotlin {
    jvmToolchain(21)
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
            srcDir("$buildDir/generated/source/openapi/main")
        }
    }
}
