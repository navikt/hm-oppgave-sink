object Database {
    const val Kotlinquery = "com.github.seratch:kotliquery:1.3.1"
}

object Fuel {
    const val version = "2.2.1"
    const val fuel = "com.github.kittinunf.fuel:fuel:$version"
    fun library(name: String) = "com.github.kittinunf.fuel:fuel-$name:$version"
}

object Jackson {
    const val version = "2.10.3"
    const val core = "com.fasterxml.jackson.core:jackson-core:$version"
    const val kotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:$version"
    const val jsr310 = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$version"
}

object Junit5 {
    const val version = "5.6.1"
    const val api = "org.junit.jupiter:junit-jupiter-api:$version"
    const val engine = "org.junit.jupiter:junit-jupiter-engine:$version"
    fun library(name: String) = "org.junit.jupiter:junit-jupiter-$name:$version"
}

object Konfig {
    const val konfig = "com.natpryce:konfig:1.6.10.0"
}

object Kotlin {
    const val version = "1.6.10"
    const val stdlib = "org.jetbrains.kotlin:kotlin-stdlib:$version"

    object Logging {
        const val version = "1.7.9"
        const val kotlinLogging = "io.github.microutils:kotlin-logging:$version"
    }
}

object KoTest {
    const val version = "5.1.0"

    // for kotest framework
    const val runner = "io.kotest:kotest-runner-junit5:$version"

    // for kotest core jvm assertion
    const val assertions = "io.kotest:kotest-assertions-core:$version"

    // for kotest property test
    const val property = "io.kotest:kotest-property-jvm:$version"

    // any other library
    fun library(name: String) = "io.kotest:kotest-$name:$version"
}

object Ktor {
    const val version = "1.6.8"
    const val serverNetty = "io.ktor:ktor-server-netty:$version"
    fun library(name: String) = "io.ktor:ktor-$name:$version"
}

object Mockk {
    const val version = "1.12.2"
    const val mockk = "io.mockk:mockk:$version"
}

object Ktlint {
    const val version = "0.38.1"
}

object Spotless {
    const val version = "6.2.0"
    const val spotless = "com.diffplug.spotless"
}

object Shadow {
    const val version = "5.2.0"
    const val shadow = "com.github.johnrengelman.shadow"
}

object Ulid {
    const val version = "8.2.0"
    const val ulid = "de.huxhorn.sulky:de.huxhorn.sulky.ulid:$version"
}

object Wiremock {
    const val version = "2.27.2"
    const val standalone = "com.github.tomakehurst:wiremock-standalone:$version"
}
