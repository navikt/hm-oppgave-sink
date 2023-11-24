rootProject.name = "hm-oppgave-sink"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://packages.confluent.io/maven/")
        maven {
            url = uri("https://maven.pkg.github.com/navikt/hm-http")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/navikt/hm-katalog")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        }
    }
    versionCatalogs {
        create("libs") {
            from("no.nav.hjelpemidler:hm-katalog:0.0.53")
        }
    }
}
