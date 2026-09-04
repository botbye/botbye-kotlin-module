package com.botbye.phishing

import com.botbye.common.BotbyeError
import com.botbye.common.BotbyeErrors
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

// fetchCatcher is an idempotent GET, so retry on connection failure: a stale pooled connection is
// re-established instead of surfacing "unexpected end of stream".
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
 * Phishing-only client, keyed by the public [BotbyePhishingConfig.clientKey] — no server key needed.
 * [fetchCatcher] proxies the asset via the `/server` route so the backend can attribute it even though
 * the browser never reaches BotBye; construction fires a best-effort init handshake.
 *
 * - [BotbyePhishingClient] `(config)` — pass `Origin` / `Referer` to [fetchCatcher] yourself.
 * - [withExtractor] — bind a [BotbyePhishingRequestExtractor] of request `R` and pass only the raw
 *   request to [fetchCatcher].
 */
class BotbyePhishingClient<R> private constructor(
    config: BotbyePhishingConfig,
    private val client: BotbyeHttpClient,
    private val extractor: BotbyePhishingRequestExtractor<R>?,
    private val ownsClient: Boolean,
) : Closeable {
    private val logger: Logger = LoggerFactory.getLogger(BotbyePhishingClient::class.java)

    // @Volatile so a concurrent setConf() publishes to other threads; each reader takes one URL, so no
    // cross-field consistency is needed.
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
        private const val FORMAT_PARAM = "format"
        private const val IMAGE_ID_PARAM = "image_id"
        private const val EXECUTABLE_PARAM = "executable"
        private const val MODULE_NAME_PARAM = "module_name"
        private const val MODULE_VERSION_PARAM = "module_version"

        // Whitelist, not blacklist: the endpoint is public, so a control param the route adds later must
        // not become forwardable by default.
        private val FORWARDABLE_PARAMS = setOf(MODULE_NAME_PARAM, MODULE_VERSION_PARAM)

        private fun errorStatus(message: String): Int =
            if (message == BotbyeErrors.TIMEOUT_ERROR) 504 else 502

        /** Standalone client. Pass [client] to reuse your transport — the SDK only [close]s its own. */
        operator fun invoke(
            config: BotbyePhishingConfig,
            client: BotbyeHttpClient? = null,
        ): BotbyePhishingClient<Nothing> = BotbyePhishingClient(
            config,
            client ?: defaultPhishingHttpClient(),
            extractor = null,
            ownsClient = client == null,
        )

        /** Framework SDKs: bind an extractor so consumers pass only their raw request. */
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

    /** Releases the transport only if this client created it. */
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
     * Fetch the catcher asset: [BotbyePhishingCatcher.Png] is the 1×1 pixel,
     * [BotbyePhishingCatcher.Svg] the wrapper that makes the browser fetch it.
     *
     * @param catcher which asset, and its parameters — `Svg` cannot be built without its `innerPngUrl`.
     * @param referer pass next to [origin]: an `<object data="…svg">` pixel sends no `Origin`.
     */
    suspend fun fetchCatcher(
        catcher: BotbyePhishingCatcher,
        origin: String?,
        referer: String?,
    ): BotbyePhishingResponse = fetchCatcherAsset(
        catcher = catcher,
        info = BotbyePhishingRequestInfo(origin = origin, referer = referer),
    )

    suspend fun fetchCatcher(
        request: R,
        catcher: BotbyePhishingCatcher,
    ): BotbyePhishingResponse = fetchCatcherAsset(
        catcher = catcher,
        info = extractRequestInfo(request),
    )

    private suspend fun fetchCatcherAsset(
        catcher: BotbyePhishingCatcher,
        info: BotbyePhishingRequestInfo,
    ): BotbyePhishingResponse {
        val catcherParams = when (catcher) {
            is BotbyePhishingCatcher.Png -> emptyMap()

            is BotbyePhishingCatcher.Svg -> mapOf(
                // Non-blank by construction; trimmed because a padded URL fails the backend's
                // absolute-URL check and silently falls back to BotBye's own PNG URL.
                IMAGE_ID_PARAM to catcher.innerPngUrl.trim(),
                EXECUTABLE_PARAM to if (catcher.skipExecution) "false" else "true",
            )
        }

        return fetchAsset(
            origin = info.origin,
            referer = info.referer,
            query = forwardable(info.query) + (FORMAT_PARAM to catcher.format) + catcherParams,
        )
    }

    private suspend fun fetchAsset(
        origin: String?,
        referer: String?,
        query: Map<String, String>,
    ): BotbyePhishingResponse {
        return try {
            // Percent-encode rather than drop: a lost Referer is the only domain an SVG pixel names.
            val headers = moduleHeaders +
                listOfNotNull(
                    usableHeaderValue(origin)?.let { "Origin" to it },
                    usableHeaderValue(referer)?.let { "Referer" to it },
                )

            val response = client.call(
                BotbyeHttpRequest(
                    url = buildImageUrl(query),
                    method = "GET",
                    headers = headers,
                ),
            )

            BotbyePhishingResponse(
                status = response.status,
                headers = response.headers,
                body = response.body,
            )
        } catch (e: Exception) {
            // Message only: a stack trace per call would flood logs during a backend outage.
            logger.warn("[BotBye] phishing image fetch failed: {}", e.message)

            val message = ErrorClassifier.classify(e)

            BotbyePhishingResponse(status = errorStatus(message), error = BotbyeError(message))
        }
    }

    private fun buildImageUrl(query: Map<String, String>): String {
        val queryString = query.entries.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

        return if (queryString.isEmpty()) phishingBaseUrl else "$phishingBaseUrl?$queryString"
    }

     private fun forwardable(query: Map<String, List<String>>): Map<String, String> =
        query.filterKeys { it in FORWARDABLE_PARAMS }
            .mapNotNull { (key, values) -> values.firstOrNull()?.let { key to it } }
            .toMap()

    private fun extractRequestInfo(request: R): BotbyePhishingRequestInfo {
        @Suppress("USELESS_ELVIS")
        return requireExtractor().extract(request) ?: BotbyePhishingRequestInfo(origin = null, referer = null)
    }

    private fun requireExtractor(): BotbyePhishingRequestExtractor<R> =
        extractor ?: error(
            "[BotBye] no phishing extractor configured; use BotbyePhishingClient.withExtractor(...) to fetch from a raw request",
        )

    /**
     * Reports the server-side phishing integration to the backend. Best-effort: never blocks the
     * customer's startup.
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
            // Fires once at construction, so log full context.
            logger.warn("[BotBye] phishing init-request exception occurred: {}", e.message, e)
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun usableHeaderValue(value: String?): String? {
        if (value.isNullOrBlank() || value.trim().equals("null", ignoreCase = true)) {
            return null
        }

        val trimmed = value.trim()
        if (trimmed.none { it >= '\u007F' || (it <= '\u001F' && it != '\t') }) {
            return trimmed
        }

        return buildString {
            for (byte in trimmed.toByteArray(StandardCharsets.UTF_8)) {
                val code = byte.toInt() and 0xFF
                if (code >= 0x7F || (code <= 0x1F && code != '\t'.code)) {
                    append('%').append(code.toString(16).uppercase().padStart(2, '0'))
                } else {
                    append(code.toChar())
                }
            }
        }
    }
}
