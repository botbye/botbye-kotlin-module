package com.botbye.protection

import com.botbye.common.BotbyeErrors
import com.botbye.common.ErrorClassifier
import com.botbye.common.ModuleInfo
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
    private var botbyeConfig: BotbyeConfig,
    private val client: BotbyeHttpClient,
    private val mapper: ObjectMapper,
    private val extractor: BotbyeRequestExtractor<R>?,
) {
    private val logger: Logger = LoggerFactory.getLogger(Botbye::class.java)
    private var evaluateBaseUrl: String = "${botbyeConfig.botbyeEndpoint}/api/v1/protect/evaluate"

    init {
        runBlocking {
            initRequest()
        }
    }

    companion object {
        /** Explicit-event client (no extractor). Build [BotbyeEvent]s yourself and call [evaluate]. */
        operator fun invoke(
            config: BotbyeConfig,
            client: BotbyeHttpClient = defaultEvaluateHttpClient(config),
            mapper: ObjectMapper = ObjectMapperFactory().createObjectMapper(),
        ): Botbye<Nothing> = Botbye(config, client, mapper, extractor = null)

        /**
         * Factory for framework SDKs: bind a [BotbyeRequestExtractor] so consumers pass only their
         * raw request object to the `evaluate*` methods.
         */
        fun <R> withExtractor(
            config: BotbyeConfig,
            extractor: BotbyeRequestExtractor<R>,
            client: BotbyeHttpClient = defaultEvaluateHttpClient(config),
            mapper: ObjectMapper = ObjectMapperFactory().createObjectMapper(),
        ): Botbye<R> = Botbye(config, client, mapper, extractor)
    }

    /** Send a fully-built event for risk evaluation. Fails open: returns ALLOW + error on failure. */
    suspend fun evaluate(event: BotbyeEvent): BotbyeEvaluateResponse {
        val tokenQuery = event.urlToken?.let { "?$it" } ?: ""
        val writer = mapper.writerFor(event::class.java).withAttribute("server_key", botbyeConfig.serverKey)

        return try {
            val response = client.call(
                BotbyeHttpRequest(
                    url = "$evaluateBaseUrl$tokenQuery",
                    method = "POST",
                    headers = moduleHeaders(),
                    body = writer.writeValueAsBytes(event),
                    contentType = botbyeConfig.contentType,
                ),
            )

            when {
                response.status >= 500 -> createFallbackEvaluationResult(BotbyeErrors.CONNECTION_ERROR)
                response.body.isEmpty() -> BotbyeEvaluateResponse()
                else -> mapper.readValue(response.body, BotbyeEvaluateResponse::class.java)
            }
        } catch (e: Exception) {
            logger.warn("[BotBye] exception occurred: {}", e.message, e)
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

    fun setConf(config: BotbyeConfig) {
        botbyeConfig = config
        evaluateBaseUrl = "${config.botbyeEndpoint}/api/v1/protect/evaluate"
    }

    private fun requireExtractor(): BotbyeRequestExtractor<R> =
        extractor ?: error(
            "[BotBye] no requestInfoExtractor configured; use Botbye.withExtractor(...) for raw-request evaluate*",
        )

    private suspend fun initRequest() {
        try {
            val response = client.call(
                BotbyeHttpRequest(
                    url = "${botbyeConfig.botbyeEndpoint.trimEnd('/')}/init-request/v1",
                    method = "POST",
                    headers = moduleHeaders(),
                    body = mapper.writeValueAsBytes(InitRequest(botbyeConfig.serverKey)),
                    contentType = botbyeConfig.contentType,
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
            logger.warn("[BotBye] exception occurred: {}", e.message, e)
        }
    }

    private fun moduleHeaders(): Map<String, String> = mapOf(
        "Module-Name" to ModuleInfo.NAME,
        "Module-Version" to ModuleInfo.VERSION,
    )
}
