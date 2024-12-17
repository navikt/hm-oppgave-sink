package no.nav.hjelpemidler.oppgave.serialization

import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.hjelpemidler.serialization.jackson.jsonMapper

fun <T : Any> MessageContext.publish(key: String, value: T) = publish(key, jsonMapper.writeValueAsString(value))
