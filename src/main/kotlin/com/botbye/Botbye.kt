package com.botbye

import com.botbye.model.ato.BotbyeAtoContext
import com.botbye.model.ato.BotbyeAtoResponse
import com.botbye.model.common.BotbyeConfig
import com.botbye.model.common.BotbyeError
import com.botbye.model.init.InitErrorResponse
import com.botbye.model.init.InitRequest
import com.botbye.model.phishing.BotbyePhishingConfig
import com.botbye.model.phishing.BotbyePhishingResponse
import com.botbye.model.validator.BotbyeRequest
import com.botbye.model.validator.BotbyeValidatorResponse
import com.botbye.model.validator.ConnectionDetails
import com.botbye.service.httpclient.OkHttpClientFactory
import com.botbye.service.httpclient.OkHttpRestClient
import com.botbye.service.httpclient.RestClient
import com.botbye.service.mapper.Headers
import com.botbye.service.mapper.ObjectMapperFactory
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger

class Botbye(
    private var botbyeConfig: BotbyeConfig,
    private var botbyePhishingConfig: BotbyePhishingConfig? = null,
    private val client: RestClient = OkHttpRestClient(OkHttpClientFactory().createClient(botbyeConfig)),
    private val mapper: ObjectMapper = ObjectMapperFactory().createObjectMapper()
) {
    private val logger: Logger = Logger.getLogger(Botbye::class.java.getName()).apply {
        level = Level.WARNING
        addHandler(ConsoleHandler())
    }

    init {
        runBlocking {
            initRequest()
        }
    }

    suspend fun validateRequest(
        token: String?,
        connectionDetails: ConnectionDetails,
        headers: Headers,
        customFields: Map<String, String> = emptyMap(),
    ): BotbyeValidatorResponse {
        val request = buildRequest(
            url = "${botbyeConfig.botbyeEndpoint}/validate-request/v2?${token.orEmpty()}",
            body = BotbyeRequest(
                serverKey = botbyeConfig.serverKey,
                headers = headers,
                requestInfo = connectionDetails,
                customFields = customFields,
            )
        )

        return try {
            handleResponse(response = client.sendRequest(request)) ?: BotbyeValidatorResponse()
        } catch (e: Exception) {
            logger.warning("[BotBye] exception occurred: ${e.message}")
            BotbyeValidatorResponse(error = BotbyeError(e.message ?: "[BotBye] failed to sendRequest"))
        }
    }

    suspend fun analyze(
        token: String?,
        atoContext: BotbyeAtoContext,
    ): BotbyeAtoResponse {
        val request = buildRequest(
            url = "${botbyeConfig.botbyeEndpoint}/analyze-context/v1?${token.orEmpty()}",
            body = atoContext
        )

        return try {
            handleResponse(response = client.sendRequest(request)) ?: BotbyeAtoResponse()
        } catch (e: Exception) {
            logger.warning("[BotBye] exception occurred: ${e.message}")
            BotbyeAtoResponse(error = BotbyeError(e.message ?: "[BotBye] failed to sendRequest"))
        }
    }

    private suspend fun initRequest() {
        val request = buildRequest(
            url = "${botbyeConfig.botbyeEndpoint.trimEnd('/')}/init-request/v1",
            body = InitRequest(botbyeConfig.serverKey)
        )

        try {
            val response = handleResponse<InitErrorResponse>(client.sendRequest(request))

            if (response?.error != null || response?.status != "ok") {
                logger.warning("[BotBye] init-request error = ${response?.error}; status = ${response?.status}")
            }
        } catch (e: Exception) {
            logger.warning("[BotBye] exception occurred: ${e.message}")
        }
    }

    suspend fun track(
        token: String?,
        atoContext: BotbyeAtoContext,
    ): BotbyeAtoResponse {
        val request = buildRequest(
            url = "${botbyeConfig.botbyeEndpoint}/track-event/v1?${token.orEmpty()}",
            body = atoContext
        )

        return try {
            handleResponse(response = client.sendRequest(request)) ?: BotbyeAtoResponse()
        } catch (e: Exception) {
            logger.warning("[BotBye] exception occurred: ${e.message}")
            BotbyeAtoResponse(error = BotbyeError(e.message ?: "[BotBye] failed to sendRequest"))
        }
    }

    fun setConf(config: BotbyeConfig) {
        botbyeConfig = config
    }

    fun setPhishingConf(config: BotbyePhishingConfig) {
        botbyePhishingConfig = config.copy()
    }

    suspend fun fetchImage(origin: String?, imageId: String? = null): BotbyePhishingResponse {
        val conf = requireNotNull(botbyePhishingConfig) { "[BotBye] phishing is not configured" }

        val baseUrl = "${conf.endpoint}/api/v1/phishing/${conf.accountId}/projects/${conf.projectId}/image"
            .toHttpUrlOrNull()
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
            .addHeader("X-Api-Key", conf.apiKey)
            .addHeader("Origin", origin ?: "origin is missing")
            .build()

        return try {
            client.sendRequest(request).use { response ->
                BotbyePhishingResponse(
                    status = response.code,
                    headers = response.headers.names().associateWith { response.header(it).orEmpty() },
                    body = response.body?.bytes() ?: byteArrayOf(),
                )
            }
        } catch (e: Exception) {
            logger.warning("[BotBye] phishing image exception occurred: ${e.message}")
            BotbyePhishingResponse(error = BotbyeError(e.message ?: "[BotBye] failed to fetch phishing image"))
        }
    }

    private fun <T> buildRequest(url: String, body: T): Request {
        return Request.Builder()
            .url(url)
            .post(mapper.writeValueAsString(body).toRequestBody(botbyeConfig.contentType))
            .apply {
                addCommonHeaders()
            }
            .build()
    }

    private fun Request.Builder.addCommonHeaders() {
        addHeader("Module-Name", BotbyeConfig.MODULE_NAME)
        addHeader("Module-Version", BotbyeConfig.MODULE_VERSION)
        addHeader("X-Botbye-Server-Key", botbyeConfig.serverKey)
    }

    private inline fun <reified T> handleResponse(response: Response): T? {
        val responseBody = response.body
            ?.use { it.string() }
            ?: return null

        return mapper.readValue(responseBody, T::class.java)
    }
}