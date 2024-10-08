package no.nav.hjelpemidler.oppgave.serialization

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import java.util.UUID

val jsonMapper: JsonMapper = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()

fun JsonNode.uuidValue(): UUID = UUID.fromString(textValue())

fun <T : Any> MessageContext.publish(key: String, value: T) = publish(key, jsonMapper.writeValueAsString(value))
