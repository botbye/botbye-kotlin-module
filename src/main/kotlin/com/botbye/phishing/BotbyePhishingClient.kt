package com.botbye.phishing

import com.botbye.common.BotbyeError
import com.botbye.common.ErrorClassifier
import com.botbye.common.ModuleInfo
import com.botbye.common.http.OkHttpClientFactory
import com.botbye.common.http.OkHttpRestClient
import com.botbye.common.http.RestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

// Phishing fetchImage is an idempotent GET, so the client retries on connection failure: a stale
// pooled keep-alive connection (closed by the server while idle) is transparently re-established
// instead of surfacing "unexpected end of stream".
private fun defaultPhishingRestClient(): RestClient =
    OkHttpRestClient(
        OkHttpClientFactory().createClient(
            maxRequests = 1500,
            maxRequestsPerHost = 1500,
            maxIdleConnections = 250,
            keepAliveDuration = Duration.ofSeconds(300),
            readTimeout = Duration.ofSeconds(2),
            writeTimeout = Duration.ofSeconds(2),
            connectionTimeout = Duration.ofSeconds(2),
            callTimeout = Duration.ofSeconds(5),
            retryOnConnectionFailure = true,
        ),
    )

/**
 * Phishing-only client. Authenticates with the public [BotbyePhishingConfig.clientKey] embedded in
 * the URL path — it needs no server key and performs no init handshake, so it can be constructed
 * independently of the evaluate [com.botbye.protection.Botbye] client.
 */
class BotbyePhishingClient(
    private var config: BotbyePhishingConfig,
    private val client: RestClient = defaultPhishingRestClient(),
) {
    private val logger: Logger = LoggerFactory.getLogger(BotbyePhishingClient::class.java)
    private var phishingBaseUrl: HttpUrl? = buildPhishingBaseUrl(config)

    fun setConf(config: BotbyePhishingConfig) {
        this.config = config.copy()
        phishingBaseUrl = buildPhishingBaseUrl(config)
    }

    private fun buildPhishingBaseUrl(conf: BotbyePhishingConfig): HttpUrl? =
        "${conf.endpoint}/api/v1/phishing/image/${conf.clientKey}"
            .toHttpUrlOrNull()

    suspend fun fetchImage(origin: String?, imageId: String? = null): BotbyePhishingResponse {
        val baseUrl = phishingBaseUrl
            ?: return BotbyePhishingResponse(error = BotbyeError("[BotBye] invalid phishing endpoint url"))

        val url = if (imageId.isNullOrBlank()) {
            baseUrl.newBuilder()
                .addQueryParameter("format", "png")
                .build()
        } else {
            baseUrl.newBuilder()
                .addQueryParameter("image_id", imageId)
                .addQueryParameter("format", "svg")
                .build()
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Origin", origin ?: "origin is missing")
            .addHeader("Module-Name", ModuleInfo.NAME)
            .addHeader("Module-Version", ModuleInfo.VERSION)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                client.sendRequest(request).use { response ->
                    val responseHeaders = buildMap(response.headers.size) {
                        for (i in 0 until response.headers.size) {
                            put(response.headers.name(i), response.headers.value(i))
                        }
                    }

                    BotbyePhishingResponse(
                        status = response.code,
                        headers = responseHeaders,
                        body = response.body?.bytes() ?: byteArrayOf(),
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("[BotBye] phishing image exception occurred: {}", e.message, e)
            BotbyePhishingResponse(error = BotbyeError(ErrorClassifier.classify(e)))
        }
    }
}
