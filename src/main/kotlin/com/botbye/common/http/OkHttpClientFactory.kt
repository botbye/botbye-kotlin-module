package com.botbye.common.http

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

class OkHttpClientFactory {
    fun createClient(
        maxRequests: Int,
        maxRequestsPerHost: Int,
        maxIdleConnections: Int,
        keepAliveDuration: Duration,
        readTimeout: Duration,
        writeTimeout: Duration,
        connectionTimeout: Duration,
        callTimeout: Duration,
        retryOnConnectionFailure: Boolean = false,
    ): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(retryOnConnectionFailure)
        .dispatcher(Dispatcher().apply {
            this.maxRequests = maxRequests
            this.maxRequestsPerHost = maxRequestsPerHost
        })
        .connectionPool(
            ConnectionPool(
                maxIdleConnections = maxIdleConnections,
                keepAliveDuration = keepAliveDuration.toMillis(),
                timeUnit = TimeUnit.MILLISECONDS,
            ),
        )
        .readTimeout(readTimeout)
        .callTimeout(callTimeout)
        .connectTimeout(connectionTimeout)
        .writeTimeout(writeTimeout)
        .build()
}
