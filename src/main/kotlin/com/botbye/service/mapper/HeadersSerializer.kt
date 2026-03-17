package com.botbye.service.mapper

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

data class Headers(val headers: Map<String, List<String>>) {
    fun toFlatMap(): Map<String, String> =
        buildMap(headers.size) {
            headers.forEach { (k, v) -> put(k.lowercase(), v.joinToString()) }
        }

    fun extractBotbyeResult(headerName: String = "X-Botbye-Result"): String? =
        headers.entries
            .firstOrNull { it.key.equals(headerName, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
}

class HeadersSerializer : JsonSerializer<Headers>() {

    override fun serialize(
        value: Headers,
        gen: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        gen.writeStartObject()
        value.headers.forEach { (key, values) ->
            gen.writeStringField(
                key.lowercase(),
                if (values.size == 1) values[0] else values.joinToString(),
            )
        }
        gen.writeEndObject()
    }
}
