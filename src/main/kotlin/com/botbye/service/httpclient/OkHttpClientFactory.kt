package com.botbye.service.httpclient

import com.botbye.model.common.BotbyeConfig
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class OkHttpClientFactory {
    fun createClient(config: BotbyeConfig): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .dispatcher(Dispatcher().apply {
            maxRequests = config.maxRequests
            maxRequestsPerHost = config.maxRequestsPerHost
        })
        .connectionPool(
            ConnectionPool(
                maxIdleConnections = config.maxIdleConnections,
                keepAliveDuration = config.keepAliveDuration.toMillis(),
                timeUnit = TimeUnit.MILLISECONDS
            )
        )
        .readTimeout(config.readTimeout)
        .callTimeout(config.callTimeout)
        .connectTimeout(config.connectionTimeout)
        .writeTimeout(config.writeTimeout)
        .build()
}
