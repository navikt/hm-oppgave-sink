package no.nav.hjelpemidler.oppgave

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

private val localProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8084",
        "application.profile" to "LOCAL",
        "kafka.reset.policy" to "earliest",
        "KAFKA_TOPIC" to "teamdigihot.hm-soknadsbehandling-v1",
        "KAFKA_TRUSTSTORE_PATH" to "",
        "KAFKA_CREDSTORE_PASSWORD" to "",
        "KAFKA_KEYSTORE_PATH" to "",
        "KAFKA_BROKERS" to "host.docker.internal:9092",
        "AZURE_TENANT_BASEURL" to "http://localhost:9098",
        "AZURE_APP_TENANT_ID" to "123",
        "AZURE_APP_CLIENT_ID" to "123",
        "AZURE_APP_CLIENT_SECRET" to "dummy",
        "oppgave.baseurl" to "http://localhost:9098/oppgave-aad",
        "PROXY_SCOPE" to "123",
        "pdl.baseurl" to "http://localhost:9098/pdl",
    )
)
private val devProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "DEV",
        "KAFKA_RESET_POLICY" to "earliest",
        "KAFKA_TOPIC" to "teamdigihot.hm-soknadsbehandling-v1",
        "pdf.baseurl" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
        "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
        "oppgave.baseurl" to "https://digihot-proxy.dev-fss-pub.nais.io/oppgave-aad",
        "PROXY_SCOPE" to "api://dev-fss.teamdigihot.digihot-proxy/.default",
        "pdl.baseurl" to "https://digihot-proxy.dev-fss-pub.nais.io/pdl-aad",
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "PROD",
        "KAFKA_RESET_POLICY" to "earliest",
        "KAFKA_TOPIC" to "teamdigihot.hm-soknadsbehandling-v1",
        "pdf.baseurl" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
        "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
        "oppgave.baseurl" to "https://digihot-proxy.prod-fss-pub.nais.io/oppgave-aad",
        "PROXY_SCOPE" to "api://8bdfd270-4760-4428-8a6e-540707d61cf9/.default",
        "pdl.baseurl" to "https://digihot-proxy.prod-fss-pub.nais.io/pdl-aad",
    )
)

private fun config() = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
    "dev-gcp" -> systemProperties() overriding EnvironmentVariables overriding devProperties
    "prod-gcp" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
    else -> {
        systemProperties() overriding EnvironmentVariables overriding localProperties
    }
}

internal object Configuration {
    val application: Application = Application()
    val azure: Azure = Azure()
    val oppgave: Oppgave = Oppgave()
    val pdl: Pdl = Pdl()
    val rapidApplication: Map<String, String> = mapOf(
        "RAPID_KAFKA_CLUSTER" to "gcp",
        "RAPID_APP_NAME" to "hm-oppgave-sink",
        "KAFKA_BROKERS" to config()[Key("KAFKA_BROKERS", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to config()[Key("KAFKA_CONSUMER_GROUP_ID", stringType)],
        "KAFKA_RAPID_TOPIC" to config()[Key("KAFKA_TOPIC", stringType)],
        "KAFKA_RESET_POLICY" to config()[Key("KAFKA_RESET_POLICY", stringType)],
        "KAFKA_TRUSTSTORE_PATH" to config()[Key("KAFKA_TRUSTSTORE_PATH", stringType)],
        "KAFKA_CREDSTORE_PASSWORD" to config()[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "KAFKA_KEYSTORE_PATH" to config()[Key("KAFKA_KEYSTORE_PATH", stringType)],
        "HTTP_PORT" to config()[Key("application.httpPort", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    data class Application(
        val id: String = config().getOrElse(Key("", stringType), "hm-oppgave-sink-v1"),
        val profile: Profile = config()[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config()[Key("application.httpPort", intType)],
        val producedEventName: String = config()[Key("PRODUCED_EVENT_NAME", stringType)],
        val consumedEventName: String = config()[Key("CONSUMED_EVENT_NAME", stringType)],
    )

    data class Azure(
        val tenantBaseUrl: String = config()[Key("AZURE_TENANT_BASEURL", stringType)],
        val tenantId: String = config()[Key("AZURE_APP_TENANT_ID", stringType)],
        val clientId: String = config()[Key("AZURE_APP_CLIENT_ID", stringType)],
        val clientSecret: String = config()[Key("AZURE_APP_CLIENT_SECRET", stringType)],
        val proxyScope: String = config()[Key("PROXY_SCOPE", stringType)]
    )

    data class Oppgave(
        val baseUrl: String = config()[Key("oppgave.baseurl", stringType)],
    )

    data class Pdl(
        val baseUrl: String = config()[Key("pdl.baseurl", stringType)],
    )
}

enum class Profile {
    LOCAL, DEV, PROD
}
