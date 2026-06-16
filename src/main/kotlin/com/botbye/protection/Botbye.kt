package com.botbye.protection

import com.botbye.common.BotbyeErrors
import com.botbye.common.ErrorClassifier
import com.botbye.common.ModuleInfo
import com.botbye.common.normalizeBaseUrl
import com.botbye.common.http.BotbyeHttpClient
import com.botbye.common.http.BotbyeHttpRequest
import com.botbye.common.http.OkHttpBotbyeClient
import com.botbye.common.http.OkHttpClientFactory
import com.botbye.protection.init.InitErrorResponse
import com.botbye.protection.init.InitRequest
import com.botbye.protection.json.ObjectMapperFactory
import com.botbye.protection.model.BotbyeEvaluateResponse
import com.botbye.protection.model.BotbyeEvent
import com.botbye.protection.model.BotbyeEventInfo
import com.botbye.protection.model.BotbyeEventStatus
import com.botbye.protection.model.BotbyeFullEvent
import com.botbye.protection.model.BotbyeRiskScoringEvent
import com.botbye.protection.model.BotbyeUserInfo
import com.botbye.protection.model.BotbyeValidationEvent
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable

private fun defaultEvaluateHttpClient(config: BotbyeConfig): BotbyeHttpClient =
    OkHttpBotbyeClient(
        OkHttpClientFactory().createClient(
            maxRequests = config.maxRequests,
            maxRequestsPerHost = config.maxRequestsPerHost,
            maxIdleConnections = config.maxIdleConnections,
            keepAliveDuration = config.keepAliveDuration,
            readTimeout = config.readTimeout,
            writeTimeout = config.writeTimeout,
            connectionTimeout = config.connectionTimeout,
            callTimeout = config.callTimeout,
        ),
    )

/**
 * Evaluate client (Level 1/2 bot & risk scoring). Requires a server key and runs an init handshake
 * on construction. Phishing image tracking lives in [com.botbye.phishing.BotbyePhishingClient].
 *
 * Two construction modes:
 * - [Botbye] `(config)` — explicit-event client. Build events yourself and call [evaluate].
 * - [withExtractor] — binds a [BotbyeRequestExtractor] of framework request `R` so callers pass only
 *   their raw request to [evaluateValidation] / [evaluateRiskScoring] / [evaluateFull].
 *
 * The type parameter [R] is the framework request type. With no extractor it is `Nothing`, which
 * makes the raw-request `evaluate*` methods uncallable at compile time.
 */
class Botbye<R> private constructor(
    initialConfig: BotbyeConfig,
    private val client: BotbyeHttpClient,
    private val mapper: ObjectMapper,
    private val extractor: BotbyeRequestExtractor<R>?,
    private val ownsClient: Boolean,
) : BotbyeEvaluator, Closeable {
    private val logger: Logger = LoggerFactory.getLogger(Botbye::class.java)

    // Read once per request into a local so a concurrent setConf() can never tear the endpoint/key apart.
    @Volatile
    private var botbyeConfig: BotbyeConfig = initialConfig

    private val moduleHeaders: Map<String, String> = mapOf(
        "Module-Name" to ModuleInfo.NAME,
        "Module-Version" to ModuleInfo.VERSION,
    )

    init {
        runBlocking {
            initRequest()
        }
    }

    companion object {
        /**
         * Explicit-event client (no extractor). Build [BotbyeEvent]s yourself and call [evaluate].
         * Pass [client] to reuse your own transport; when omitted the SDK creates and owns a default
         * OkHttp client that [close] will shut down (a passed-in client is never closed by the SDK).
         */
        operator fun invoke(
            config: BotbyeConfig,
            client: BotbyeHttpClient? = null,
            mapper: ObjectMapper = ObjectMapperFactory().createObjectMapper(),
        ): Botbye<Nothing> = Botbye(
            config,
            client ?: defaultEvaluateHttpClient(config),
            mapper,
            extractor = null,
            ownsClient = client == null,
        )

        /**
         * Factory for framework SDKs: bind a [BotbyeRequestExtractor] so consumers pass only their
         * raw request object to the `evaluate*` methods.
         */
        fun <R> withExtractor(
            config: BotbyeConfig,
            extractor: BotbyeRequestExtractor<R>,
            client: BotbyeHttpClient? = null,
            mapper: ObjectMapper = ObjectMapperFactory().createObjectMapper(),
        ): Botbye<R> = Botbye(
            config,
            client ?: defaultEvaluateHttpClient(config),
            mapper,
            extractor,
            ownsClient = client == null,
        )
    }

    /** Send a fully-built event for risk evaluation. Fails open: returns ALLOW + error on failure. */
    override suspend fun evaluate(event: BotbyeEvent): BotbyeEvaluateResponse {
        val config = botbyeConfig
        val tokenQuery = event.urlToken?.let { "?$it" } ?: ""
        val writer = mapper.writerFor(event::class.java).withAttribute("server_key", config.serverKey)

        return try {
            val response = client.call(
                BotbyeHttpRequest(
                    url = "${normalizeBaseUrl(config.botbyeEndpoint)}/api/v1/protect/evaluate$tokenQuery",
                    method = "POST",
                    headers = moduleHeaders,
                    body = writer.writeValueAsBytes(event),
                    contentType = config.contentType,
                ),
            )

            when {
                response.status >= 500 -> createFallbackEvaluationResult(BotbyeErrors.CONNECTION_ERROR)
                response.body.isEmpty() -> BotbyeEvaluateResponse()
                else -> mapper.readValue(response.body, BotbyeEvaluateResponse::class.java)
            }
        } catch (e: Exception) {
            // Message only: under a backend outage this fires per request, so a full stack trace per
            // call would flood logs exactly when the system is already stressed.
            logger.warn("[BotBye] evaluate failed, failing open: {}", e.message)
            createFallbackEvaluationResult(ErrorClassifier.classify(e))
        }
    }

    /** Level 1 bot validation from a raw framework request (requires [withExtractor]). */
    suspend fun evaluateValidation(
        request: R,
        token: String? = null,
        customFields: Map<String, String> = emptyMap(),
    ): BotbyeEvaluateResponse {
        val info = requireExtractor().extract(request)

        return evaluate(
            BotbyeValidationEvent(
                request = info.copy(token = token ?: info.token),
                customFields = customFields,
            ),
        )
    }

    /** Level 2 risk evaluation from a raw framework request (requires [withExtractor]). */
    suspend fun evaluateRiskScoring(
        request: R,
        user: BotbyeUserInfo,
        eventType: String,
        eventStatus: BotbyeEventStatus,
        token: String? = null,
        botbyeResult: String? = null,
        customFields: Map<String, String> = emptyMap(),
    ): BotbyeEvaluateResponse {
        val info = requireExtractor().extract(request)

        return evaluate(
            BotbyeRiskScoringEvent(
                request = info.copy(token = token ?: info.token),
                event = BotbyeEventInfo(type = eventType, status = eventStatus),
                user = user,
                botbyeResult = botbyeResult?.takeIf { it.isNotBlank() },
                customFields = customFields,
            ),
        )
    }

    /** Combined Level 1+2 evaluation from a raw framework request (requires [withExtractor]). */
    suspend fun evaluateFull(
        request: R,
        user: BotbyeUserInfo,
        eventType: String,
        eventStatus: BotbyeEventStatus,
        token: String? = null,
        customFields: Map<String, String> = emptyMap(),
    ): BotbyeEvaluateResponse {
        val info = requireExtractor().extract(request)

        return evaluate(
            BotbyeFullEvent(
                request = info.copy(token = token ?: info.token),
                event = BotbyeEventInfo(type = eventType, status = eventStatus),
                user = user,
                customFields = customFields,
            ),
        )
    }

    override fun setConf(config: BotbyeConfig) {
        botbyeConfig = config
    }

    /** Releases the underlying transport only if this client created it (a passed-in client is left alone). */
    override fun close() {
        if (ownsClient) {
            client.close()
        }
    }

    private fun requireExtractor(): BotbyeRequestExtractor<R> =
        extractor ?: error(
            "[BotBye] no requestInfoExtractor configured; use Botbye.withExtractor(...) for raw-request evaluate*",
        )

    private suspend fun initRequest() {
        val config = botbyeConfig
        try {
            val response = client.call(
                BotbyeHttpRequest(
                    url = "${normalizeBaseUrl(config.botbyeEndpoint)}/init-request/v1",
                    method = "POST",
                    headers = moduleHeaders,
                    body = mapper.writeValueAsBytes(InitRequest(config.serverKey)),
                    contentType = config.contentType,
                ),
            )

            val parsed = if (response.body.isEmpty()) {
                null
            } else {
                mapper.readValue(response.body, InitErrorResponse::class.java)
            }

            if (parsed?.error != null || parsed?.status != "ok") {
                logger.warn("[BotBye] init-request error = {}; status = {}", parsed?.error, parsed?.status)
            }
        } catch (e: Exception) {
            // Best-effort handshake at construction time; log full context (fires once, not per request).
            logger.warn("[BotBye] init-request exception occurred: {}", e.message, e)
        }
    }
}
