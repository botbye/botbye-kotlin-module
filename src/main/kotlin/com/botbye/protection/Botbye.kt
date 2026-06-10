package com.botbye.protection

import com.botbye.common.BotbyeError
import com.botbye.common.ErrorClassifier
import com.botbye.common.http.OkHttpClientFactory
import com.botbye.common.http.OkHttpRestClient
import com.botbye.common.http.RestClient
import com.botbye.protection.init.InitErrorResponse
import com.botbye.protection.init.InitRequest
import com.botbye.protection.json.ObjectMapperFactory
import com.botbye.protection.model.BotbyeEvaluateResponse
import com.botbye.protection.model.BotbyeEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private fun defaultEvaluateRestClient(config: BotbyeConfig): RestClient =
    OkHttpRestClient(
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
 */
class Botbye(
    private var botbyeConfig: BotbyeConfig,
    private val client: RestClient = defaultEvaluateRestClient(botbyeConfig),
    private val mapper: ObjectMapper = ObjectMapperFactory().createObjectMapper(),
) {
    private val logger: Logger = LoggerFactory.getLogger(Botbye::class.java)
    private var evaluateBaseUrl: String = "${botbyeConfig.botbyeEndpoint}/api/v1/protect/evaluate"

    init {
        runBlocking {
            initRequest()
        }
    }

    private suspend fun initRequest() {
        val request = buildRequest(
            url = "${botbyeConfig.botbyeEndpoint.trimEnd('/')}/init-request/v1",
            body = InitRequest(botbyeConfig.serverKey),
        )

        try {
            val response = handleResponse<InitErrorResponse>(client.sendRequest(request))

            if (response?.error != null || response?.status != "ok") {
                logger.warn("[BotBye] init-request error = {}; status = {}", response?.error, response?.status)
            }
        } catch (e: Exception) {
            logger.warn("[BotBye] exception occurred: {}", e.message, e)
        }
    }

    suspend fun evaluate(event: BotbyeEvent): BotbyeEvaluateResponse {
        val tokenQuery = event.urlToken?.let { "?$it" } ?: ""
        val writer = mapper.writerFor(event::class.java).withAttribute("server_key", botbyeConfig.serverKey)
        val httpRequest = buildEvaluateHttpRequest(
            url = "$evaluateBaseUrl$tokenQuery",
            writer = writer,
            request = event,
        )

        return try {
            handleResponse(response = client.sendRequest(httpRequest), checkStatus = true) ?: BotbyeEvaluateResponse()
        } catch (e: Exception) {
            logger.warn("[BotBye] exception occurred: {}", e.message, e)
            BotbyeEvaluateResponse(
                error = BotbyeError(ErrorClassifier.classify(e)),
            )
        }
    }

    fun setConf(config: BotbyeConfig) {
        botbyeConfig = config
        evaluateBaseUrl = "${config.botbyeEndpoint}/api/v1/protect/evaluate"
    }

    private fun buildEvaluateHttpRequest(url: String, writer: ObjectWriter, request: BotbyeEvent): Request {
        val body = writer.writeValueAsBytes(request)
            .toRequestBody(botbyeConfig.contentType)

        return Request.Builder()
            .url(url)
            .post(body)
            .apply { addCommonHeaders() }
            .build()
    }

    private fun <T> buildRequest(url: String, body: T): Request {
        return Request.Builder()
            .url(url)
            .post(mapper.writeValueAsBytes(body).toRequestBody(botbyeConfig.contentType))
            .apply { addCommonHeaders() }
            .build()
    }

    private fun Request.Builder.addCommonHeaders() {
        addHeader("Module-Name", BotbyeConfig.MODULE_NAME)
        addHeader("Module-Version", BotbyeConfig.MODULE_VERSION)
    }

    private suspend inline fun <reified T> handleResponse(response: Response, checkStatus: Boolean = false): T? {
        if (checkStatus && response.code >= 500) {
            response.close()
            throw java.io.IOException("connection error: HTTP ${response.code}")
        }

        val body = response.body ?: run {
            response.close()

            return null
        }

        return withContext(Dispatchers.IO) {
            response.use {
                val responseBody = body.string()

                mapper.readValue(responseBody, T::class.java)
            }
        }
    }
}
