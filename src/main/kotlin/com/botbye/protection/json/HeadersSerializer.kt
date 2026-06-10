package com.botbye.protection.json

import com.botbye.common.http.Headers
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

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
