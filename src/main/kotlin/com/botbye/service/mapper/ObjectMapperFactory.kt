package com.botbye.service.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import java.time.Instant

class ObjectMapperFactory {
    fun createObjectMapper(): ObjectMapper = ObjectMapper()
        .registerModule(SimpleModule()
            .addSerializer(Headers::class.java, HeadersSerializer())
            .addSerializer(Instant::class.java, InstantSerializer())
        )
}