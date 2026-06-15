package com.botbye.phishing

import com.botbye.common.BotbyeError
import com.botbye.common.ErrorClassifier
import com.botbye.common.ModuleInfo
import com.botbye.common.http.BotbyeHttpClient
import com.botbye.common.http.BotbyeHttpRequest
import com.botbye.common.http.OkHttpBotbyeClient
import com.botbye.common.http.OkHttpClientFactory
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

// Phishing fetchImage is an idempotent GET, so the client retries on connection failure: a stale
// pooled keep-alive connection (closed by the server while idle) is transparently re-established
// instead of surfacing "unexpected end of stream".
private fun defaultPhishingHttpClient(): BotbyeHttpClient =
    OkHttpBotbyeClient(
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
 *
 * Two construction modes:
 * - [BotbyePhishingClient] `(config)` — pass the `Origin` header to [fetchImage] yourself.
 * - [withExtractor] — binds a [BotbyePhishingRequestExtractor] of framework request `R` so callers
 *   pass only their raw request to [fetchImage].
 */
class BotbyePhishingClient<R> private constructor(
    private var config: BotbyePhishingConfig,
    private val client: BotbyeHttpClient,
    private val extractor: BotbyePhishingRequestExtractor<R>?,
) {
    private val logger: Logger = LoggerFactory.getLogger(BotbyePhishingClient::class.java)
    private var phishingBaseUrl: String = buildPhishingBaseUrl(config)
    private var phishingInitUrl: String = buildPhishingInitUrl(config)

    init {
        runBlocking {
            sendInit()
        }
    }

    companion object {
        /** Standalone client; pass the `Origin` header to [fetchImage] explicitly. */
        operator fun invoke(
            config: BotbyePhishingConfig,
            client: BotbyeHttpClient = defaultPhishingHttpClient(),
        ): BotbyePhishingClient<Nothing> = BotbyePhishingClient(config, client, extractor = null)

        /**
         * Factory for framework SDKs: bind a [BotbyePhishingRequestExtractor] so consumers pass only
         * their raw request object to [fetchImage].
         */
        fun <R> withExtractor(
            config: BotbyePhishingConfig,
            extractor: BotbyePhishingRequestExtractor<R>,
            client: BotbyeHttpClient = defaultPhishingHttpClient(),
        ): BotbyePhishingClient<R> = BotbyePhishingClient(config, client, extractor)
    }

    fun setConf(config: BotbyePhishingConfig) {
        this.config = config.copy()
        phishingBaseUrl = buildPhishingBaseUrl(config)
        phishingInitUrl = buildPhishingInitUrl(config)
    }

    private fun buildPhishingBaseUrl(conf: BotbyePhishingConfig): String =
        "${conf.endpoint}/api/v1/phishing/image/${conf.clientKey}/server"

    private fun buildPhishingInitUrl(conf: BotbyePhishingConfig): String =
        "${conf.endpoint}/api/v1/phishing/init-request/v1/${conf.clientKey}"

    /** Fetch the tracking pixel using an explicit `Origin` header value. */
    suspend fun fetchImage(origin: String?, imageId: String? = null): BotbyePhishingResponse {
        val url = if (imageId.isNullOrBlank()) {
            "$phishingBaseUrl?format=png"
        } else {
            "$phishingBaseUrl?image_id=${urlEncode(imageId)}&format=svg"
        }

        return try {
            val response = client.call(
                BotbyeHttpRequest(
                    url = url,
                    method = "GET",
                    headers = moduleHeaders() + ("Origin" to (origin ?: "origin is missing")),
                ),
            )

            BotbyePhishingResponse(
                status = response.status,
                headers = response.headers,
                body = response.body,
            )
        } catch (e: Exception) {
            logger.warn("[BotBye] phishing image exception occurred: {}", e.message, e)
            BotbyePhishingResponse(error = BotbyeError(ErrorClassifier.classify(e)))
        }
    }

    /** Fetch the tracking pixel from a raw framework request (requires [withExtractor]). */
    suspend fun fetchImage(request: R, imageId: String? = null): BotbyePhishingResponse {
        val origin = requireExtractor().extractOrigin(request)

        return fetchImage(origin = origin, imageId = imageId)
    }

    private fun requireExtractor(): BotbyePhishingRequestExtractor<R> =
        extractor ?: error(
            "[BotBye] no phishing extractor configured; use BotbyePhishingClient.withExtractor(...) to fetch from a raw request",
        )

    /**
     * Reports the server-side phishing integration to the backend (the `SERVER_INTEGRATION_INIT`
     * get-started milestone). Best-effort: any failure is logged and swallowed, mirroring the evaluate
     * client's init handshake, since it must never block or break the customer's startup.
     */
    private suspend fun sendInit() {
        try {
            val response = client.call(
                BotbyeHttpRequest(
                    url = phishingInitUrl,
                    method = "POST",
                    headers = moduleHeaders(),
                    body = ByteArray(0),
                ),
            )

            if (response.status !in 200..299) {
                logger.warn("[BotBye] phishing init-request returned HTTP {}", response.status)
            }
        } catch (e: Exception) {
            logger.warn("[BotBye] phishing init-request exception occurred: {}", e.message, e)
        }
    }

    private fun moduleHeaders(): Map<String, String> = mapOf(
        "Module-Name" to ModuleInfo.NAME,
        "Module-Version" to ModuleInfo.VERSION,
    )

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
