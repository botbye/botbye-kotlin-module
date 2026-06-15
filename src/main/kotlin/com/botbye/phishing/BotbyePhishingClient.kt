package com.botbye.phishing

import com.botbye.common.BotbyeError
import com.botbye.common.ErrorClassifier
import com.botbye.common.ModuleInfo
import com.botbye.common.http.OkHttpClientFactory
import com.botbye.common.http.OkHttpRestClient
import com.botbye.common.http.RestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
 * the URL path — it needs no server key, so it can be constructed independently of the evaluate
 * [com.botbye.protection.Botbye] client.
 *
 * On construction it fires a best-effort server-integration init handshake
 * (`POST /api/v1/phishing/init-request/v1/{clientKey}`), reporting this module via the `Module-Name`
 * / `Module-Version` headers. [fetchImage] fetches the tracking pixel server-side via the `/server`
 * route, which lets the backend attribute the pixel to this module even when the browser never hits
 * BotBye directly (the SDK proxies the image).
 */
class BotbyePhishingClient(
    private var config: BotbyePhishingConfig,
    private val client: RestClient = defaultPhishingRestClient(),
) {
    private val logger: Logger = LoggerFactory.getLogger(BotbyePhishingClient::class.java)
    private var phishingBaseUrl: HttpUrl? = buildPhishingBaseUrl(config)
    private var phishingInitUrl: HttpUrl? = buildPhishingInitUrl(config)

    init {
        runBlocking {
            sendInit()
        }
    }

    fun setConf(config: BotbyePhishingConfig) {
        this.config = config.copy()
        phishingBaseUrl = buildPhishingBaseUrl(config)
        phishingInitUrl = buildPhishingInitUrl(config)
    }

    private fun buildPhishingBaseUrl(conf: BotbyePhishingConfig): HttpUrl? =
        "${conf.endpoint}/api/v1/phishing/image/${conf.clientKey}/server"
            .toHttpUrlOrNull()

    private fun buildPhishingInitUrl(conf: BotbyePhishingConfig): HttpUrl? =
        "${conf.endpoint}/api/v1/phishing/init-request/v1/${conf.clientKey}"
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
            .apply { addModuleHeaders() }
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

    /**
     * Reports the server-side phishing integration to the backend (the `SERVER_INTEGRATION_INIT`
     * get-started milestone). Best-effort: any failure is logged and swallowed, mirroring the evaluate
     * client's init handshake, since it must never block or break the customer's startup.
     */
    private suspend fun sendInit() {
        val url = phishingInitUrl ?: run {
            logger.warn("[BotBye] invalid phishing init url")

            return
        }

        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .apply { addModuleHeaders() }
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.sendRequest(request).use { response ->
                    if (!response.isSuccessful) {
                        logger.warn("[BotBye] phishing init-request returned HTTP {}", response.code)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("[BotBye] phishing init-request exception occurred: {}", e.message, e)
        }
    }

    private fun Request.Builder.addModuleHeaders() {
        addHeader("Module-Name", ModuleInfo.NAME)
        addHeader("Module-Version", ModuleInfo.VERSION)
    }
}
