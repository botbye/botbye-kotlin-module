package com.botbye

import com.botbye.model.ato.BotbyeAtoContext
import com.botbye.model.ato.BotbyeAtoResponse
import com.botbye.model.common.BotbyeConfig
import com.botbye.model.common.BotbyeError
import com.botbye.model.validator.BotbyeRequest
import com.botbye.model.validator.BotbyeValidatorResponse
import com.botbye.model.validator.ConnectionDetails
import com.botbye.service.httpclient.OkHttpClientFactory
import com.botbye.service.httpclient.OkHttpRestClient
import com.botbye.service.httpclient.RestClient
import com.botbye.service.mapper.Headers
import com.botbye.service.mapper.ObjectMapperFactory
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger

class Botbye(
    private var botbyeConfig: BotbyeConfig,
    private val client: RestClient = OkHttpRestClient(OkHttpClientFactory().createClient(botbyeConfig)),
    private val mapper: ObjectMapper = ObjectMapperFactory().createObjectMapper()
) {
    init {
        if (botbyeConfig.serverKey.isBlank()) error("[BotBye] server key is not specified")
    }

    private val logger: Logger = Logger.getLogger(Botbye::class.java.getName()).apply {
        level = Level.WARNING
        addHandler(ConsoleHandler())
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

    fun setConf(config: BotbyeConfig) {
        botbyeConfig = config
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
