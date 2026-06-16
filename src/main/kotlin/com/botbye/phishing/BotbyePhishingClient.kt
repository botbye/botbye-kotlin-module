package com.botbye.phishing

import com.botbye.common.BotbyeError
import com.botbye.common.ErrorClassifier
import com.botbye.common.ModuleInfo
import com.botbye.common.normalizeBaseUrl
import com.botbye.common.http.BotbyeHttpClient
import com.botbye.common.http.BotbyeHttpRequest
import com.botbye.common.http.OkHttpBotbyeClient
import com.botbye.common.http.OkHttpClientFactory
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
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
    config: BotbyePhishingConfig,
    private val client: BotbyeHttpClient,
    private val extractor: BotbyePhishingRequestExtractor<R>?,
    private val ownsClient: Boolean,
) : Closeable {
    private val logger: Logger = LoggerFactory.getLogger(BotbyePhishingClient::class.java)

    // @Volatile so a concurrent setConf() publishes the new URLs to other threads; fetchImage / sendInit
    // each read one of these, so no cross-field consistency is required between them.
    @Volatile
    private var phishingBaseUrl: String = buildPhishingBaseUrl(config)

    @Volatile
    private var phishingInitUrl: String = buildPhishingInitUrl(config)

    private val moduleHeaders: Map<String, String> = mapOf(
        "Module-Name" to ModuleInfo.NAME,
        "Module-Version" to ModuleInfo.VERSION,
    )

    init {
        runBlocking {
            sendInit()
        }
    }

    companion object {
        /**
         * Standalone client; pass the `Origin` header to [fetchImage] explicitly. Pass [client] to
         * reuse your own transport; when omitted the SDK creates and owns a default OkHttp client that
         * [close] will shut down (a passed-in client is never closed by the SDK).
         */
        operator fun invoke(
            config: BotbyePhishingConfig,
            client: BotbyeHttpClient? = null,
        ): BotbyePhishingClient<Nothing> = BotbyePhishingClient(
            config,
            client ?: defaultPhishingHttpClient(),
            extractor = null,
            ownsClient = client == null,
        )

        /**
         * Factory for framework SDKs: bind a [BotbyePhishingRequestExtractor] so consumers pass only
         * their raw request object to [fetchImage].
         */
        fun <R> withExtractor(
            config: BotbyePhishingConfig,
            extractor: BotbyePhishingRequestExtractor<R>,
            client: BotbyeHttpClient? = null,
        ): BotbyePhishingClient<R> = BotbyePhishingClient(
            config,
            client ?: defaultPhishingHttpClient(),
            extractor,
            ownsClient = client == null,
        )
    }

    fun setConf(config: BotbyePhishingConfig) {
        phishingBaseUrl = buildPhishingBaseUrl(config)
        phishingInitUrl = buildPhishingInitUrl(config)
    }

    /** Releases the underlying transport only if this client created it (a passed-in client is left alone). */
    override fun close() {
        if (ownsClient) {
            client.close()
        }
    }

    private fun buildPhishingBaseUrl(conf: BotbyePhishingConfig): String =
        "${normalizeBaseUrl(conf.endpoint)}/api/v1/phishing/image/${conf.clientKey}/server"

    private fun buildPhishingInitUrl(conf: BotbyePhishingConfig): String =
        "${normalizeBaseUrl(conf.endpoint)}/api/v1/phishing/init-request/v1/${conf.clientKey}"

    /**
     * Fetch the tracking pixel using an explicit `Origin` header value. [query] is forwarded verbatim
     * to the `/server` route — pass the browser's original pixel query (which carries `format`,
     * `image_id`, and the JS tag's `module_name` / `module_version`).
     */
    suspend fun fetchImage(origin: String?, query: Map<String, String> = emptyMap()): BotbyePhishingResponse {
        return try {
            val response = client.call(
                BotbyeHttpRequest(
                    url = buildImageUrl(query),
                    method = "GET",
                    headers = moduleHeaders + ("Origin" to (origin ?: "origin is missing")),
                ),
            )

            BotbyePhishingResponse(
                status = response.status,
                headers = response.headers,
                body = response.body,
            )
        } catch (e: Exception) {
            // Message only: this is on the per-request pixel path, so a stack trace per call would
            // flood logs during a backend outage.
            logger.warn("[BotBye] phishing image fetch failed: {}", e.message)
            BotbyePhishingResponse(error = BotbyeError(ErrorClassifier.classify(e)))
        }
    }

    /** Fetch the tracking pixel from a raw framework request (requires [withExtractor]). */
    suspend fun fetchImage(request: R, query: Map<String, String> = emptyMap()): BotbyePhishingResponse {
        val origin = requireExtractor().extractOrigin(request)

        return fetchImage(origin = origin, query = query)
    }

    private fun buildImageUrl(query: Map<String, String>): String {
        if (query.isEmpty()) {
            return phishingBaseUrl
        }

        val queryString = query.entries.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

        return "$phishingBaseUrl?$queryString"
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
                    headers = moduleHeaders,
                    body = ByteArray(0),
                ),
            )

            if (response.status !in 200..299) {
                logger.warn("[BotBye] phishing init-request returned HTTP {}", response.status)
            }
        } catch (e: Exception) {
            // Best-effort handshake at construction time; log full context (fires once, not per request).
            logger.warn("[BotBye] phishing init-request exception occurred: {}", e.message, e)
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
