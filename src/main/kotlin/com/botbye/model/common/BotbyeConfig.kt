package com.botbye.model.common

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import java.time.Duration

data class BotbyeConfig(
    var botbyeEndpoint: String = "https://verify.botbye.com",
    var serverKey: String,
    var contentType: MediaType = "application/json".toMediaType(),
    // client config
    val readTimeout: Duration = Duration.ofSeconds(2),
    val writeTimeout: Duration = Duration.ofSeconds(2),
    val connectionTimeout: Duration = Duration.ofSeconds(2),
    val callTimeout: Duration = Duration.ofSeconds(5),
    // pool config
    val maxIdleConnections: Int = 250,
    val keepAliveDuration: Duration = Duration.ofSeconds(300),
    // dispatcher
    val maxRequestsPerHost: Int = 1500,
    val maxRequests: Int = 1500,
) {
    init {
        require(serverKey.isNotBlank()) { "[BotBye] server key is not specified" }
        botbyeEndpoint = normalizeBaseUrl(botbyeEndpoint)
    }

    companion object {
        const val MODULE_NAME = "Kotlin"
        const val MODULE_VERSION = "1.0.1"
    }
}

fun normalizeBaseUrl(url: String): String = url.trimEnd('/')
