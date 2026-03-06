package com.botbye.service.httpclient

import com.botbye.model.common.BotbyeConfig
import com.botbye.model.phishing.BotbyePhishingConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PhishingRestClientFactory(
    private val okHttpClientFactory: OkHttpClientFactory = OkHttpClientFactory(),
) {
    @Volatile
    private var cachedClient: RestClient? = null

    private val mutex = Mutex()

    fun reset() {
        cachedClient = null
    }

    suspend fun getOrCreate(botbyeConfig: BotbyeConfig, phishingConfig: BotbyePhishingConfig): RestClient {
        cachedClient?.let { return it }

        return mutex.withLock {
            cachedClient?.let { return it }

            val configured = phishingConfig.requireConfigured()
            val newClient = OkHttpRestClient(okHttpClientFactory.createClient(botbyeConfig, configured))
            cachedClient = newClient
            newClient
        }
    }
}
