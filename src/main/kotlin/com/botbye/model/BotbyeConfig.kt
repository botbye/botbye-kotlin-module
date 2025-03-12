package com.botbye.model

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import java.time.Duration
import java.util.concurrent.TimeUnit

data class BotbyeConfig(
    var botbyeEndpoint: String = "https://verify.botbye.com",
    var serverKey: String,
    var path: String = "/validate-request/v2",
    var contentType: MediaType = "application/json".toMediaType(),
    // client config
    val readTimeout: Duration = Duration.ofSeconds(2),
    val writeTimeout: Duration = Duration.ofSeconds(2),
    val connectionTimeout: Duration = Duration.ofSeconds(2),
    val callTimeout: Duration = Duration.ofSeconds(5),
    // pool config
    val maxIdleConnections: Int = 250,
    val keepAliveDuration: Long = 5,
    val keepAliveDurationTimeUnit: TimeUnit = TimeUnit.MINUTES,
    // dispatcher
    val maxRequestsPerHost: Int = 1500,
    val maxRequests: Int = 1500,
) {
    companion object {
        const val MODULE_NAME = "Kotlin"
        const val MODULE_VERSION = "0.0.5"
    }
}
