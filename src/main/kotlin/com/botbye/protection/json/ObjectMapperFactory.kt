package com.botbye.protection.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.botbye.common.http.Headers
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.Instant

class ObjectMapperFactory {
    fun createObjectMapper(): ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(
            SimpleModule()
                .addSerializer(Headers::class.java, HeadersSerializer())
                .addSerializer(Instant::class.java, InstantSerializer()),
        )
}