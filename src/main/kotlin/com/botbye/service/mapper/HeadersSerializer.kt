package com.botbye.service.mapper

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

data class Headers(val headers: Map<String, List<String>>)

class HeadersSerializer : JsonSerializer<Headers>() {

    override fun serialize(
        value: Headers,
        gen: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        val result = value.headers.entries.associate { (header, values) -> header to values.joinToString() }
        gen.writeObject(result)
    }
}
