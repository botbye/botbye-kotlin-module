package com.botbye

import com.botbye.model.common.BotbyeConfig
import com.botbye.model.common.BotbyeError
import com.botbye.model.evaluate.BotbyeEvaluateResponse
import com.botbye.model.evaluate.BotbyeEvent
import com.botbye.model.init.InitErrorResponse
import com.botbye.model.init.InitRequest
import com.botbye.model.phishing.BotbyePhishingConfig
import com.botbye.model.phishing.BotbyePhishingResponse
import com.botbye.service.httpclient.OkHttpClientFactory
import com.botbye.service.httpclient.OkHttpRestClient
import com.botbye.service.httpclient.RestClient
import com.botbye.service.mapper.ObjectMapperFactory
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Derives a phishing RestClient that shares the dispatcher + connection pool of [base]
 * but enables retryOnConnectionFailure (safe for the idempotent fetchImage GET). Falls
 * back to [base] if it is not an OkHttp-backed client (e.g. a test double).
 */
private fun phishingRestClientFrom(base: RestClient): RestClient =
    if (base is OkHttpRestClient) {
        OkHttpRestClient(base.client.newBuilder().retryOnConnectionFailure(true).build())
    } else {
        base
    }

class Botbye(
    private var botbyeConfig: BotbyeConfig,
    private var botbyePhishingConfig: BotbyePhishingConfig? = null,
    private val client: RestClient = OkHttpRestClient(OkHttpClientFactory().createClient(botbyeConfig)),
    private val mapper: ObjectMapper = ObjectMapperFactory().createObjectMapper(),
    // Phishing fetchImage is an idempotent GET; it reuses the same dispatcher + connection
    // pool as [client] but with retryOnConnectionFailure = true, so a stale pooled keep-alive
    // connection (closed by the server while idle) is transparently re-established instead of
    // surfacing "unexpected end of stream".
    private val phishingClient: RestClient = phishingRestClientFrom(client),
) {
    private val logger: Logger = LoggerFactory.getLogger(Botbye::class.java)
    private var evaluateBaseUrl: String = "${botbyeConfig.botbyeEndpoint}/api/v1/protect/evaluate"
    private var phishingBaseUrl: HttpUrl? = botbyePhishingConfig?.let { buildPhishingBaseUrl(it) }



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
                error = BotbyeError(classifyError(e)),
            )
        }
    }

    fun setConf(config: BotbyeConfig) {
        botbyeConfig = config
        evaluateBaseUrl = "${config.botbyeEndpoint}/api/v1/protect/evaluate"
    }

    fun setPhishingConf(config: BotbyePhishingConfig) {
        botbyePhishingConfig = config.copy()
        phishingBaseUrl = buildPhishingBaseUrl(config)
    }

    private fun buildPhishingBaseUrl(conf: BotbyePhishingConfig): HttpUrl? =
        "${conf.endpoint}/api/v1/phishing/image/${conf.clientKey}"
            .toHttpUrlOrNull()

    suspend fun fetchImage(origin: String?, imageId: String? = null): BotbyePhishingResponse {
        val conf = requireNotNull(botbyePhishingConfig) { "[BotBye] phishing is not configured" }

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
            .build()

        return try {
            withContext(Dispatchers.IO) {
                phishingClient.sendRequest(request).use { response ->
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
            BotbyePhishingResponse(error = BotbyeError(classifyError(e)))
        }
    }

    private fun classifyError(e: Exception): String = when {
        e is SocketTimeoutException -> "timeout"
        e is ConnectException -> "connection error"
        e is JsonProcessingException -> "invalid json response"
        e is java.io.IOException -> "connection error"
        e.message?.startsWith("connection error") == true -> "connection error"
        else -> e.message ?: "unknown error"
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
