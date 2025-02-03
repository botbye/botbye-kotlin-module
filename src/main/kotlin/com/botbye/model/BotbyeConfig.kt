package com.botbye.model

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

data class BotbyeConfig(
    var botbyeEndpoint: String = "https://verify.botbye.com",
    var serverKey: String,
    var path: String = "/validate-request/v2",
    var connectionTimeout: Long = 1L,
    var connectionTimeoutUnit: TimeUnit = TimeUnit.SECONDS,
    var connectionPoolSize: Int = 5,
    var keepAliveDuration: Long = 5L,
    var keepAliveDurationTimeUnit: TimeUnit = TimeUnit.MINUTES,
    var contentType: MediaType = "application/json".toMediaType(),
    val moduleVersion: String = "0.0.2",
    val moduleName: String = "Kotlin",
)
