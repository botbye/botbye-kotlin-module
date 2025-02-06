package com.botbye

import com.botbye.model.BotbyeConfig
import com.botbye.model.BotbyeError
import com.botbye.model.BotbyeRequest
import com.botbye.model.BotbyeResponse
import com.botbye.model.ConnectionDetails
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.module.SimpleModule
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val LOGGER = Logger.getLogger(Botbye::class.java.getName())
private val mapper = ObjectMapper()

class Botbye(
    private var botbyeConfig: BotbyeConfig,
) {

    private val writer: ObjectWriter = mapper.registerModule(
        SimpleModule().addSerializer(Headers::class.java, HeadersSerializer()),
    ).writer()

    private val reader: ObjectReader = mapper.reader()

    private val client: OkHttpClient = OkHttpClient
        .Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = botbyeConfig.maxRequests
            maxRequestsPerHost = botbyeConfig.maxRequestsPerHost
        })
        .connectionPool(
            ConnectionPool(
                maxIdleConnections = botbyeConfig.maxIdleConnections,
                keepAliveDuration = botbyeConfig.keepAliveDuration,
                timeUnit = botbyeConfig.keepAliveDurationTimeUnit
            )
        )
        .readTimeout(botbyeConfig.readTimeout)
        .callTimeout(botbyeConfig.callTimeout)
        .connectTimeout(botbyeConfig.connectionTimeout)
        .writeTimeout(botbyeConfig.writeTimeout)
        .build()

    init {
        LOGGER.setLevel(Level.WARNING)
        LOGGER.addHandler(ConsoleHandler())
    }

    private fun handleResponse(response: Response): BotbyeResponse {
        val responseBody = response.body
            ?.use { it.string() }
            ?: return BotbyeResponse()

        return reader.readValue(responseBody, BotbyeResponse::class.java)
    }

    fun setConf(config: BotbyeConfig) {
        botbyeConfig = config
    }

    suspend fun validateRequest(
        token: String?,
        connectionDetails: ConnectionDetails,
        headers: Headers,
        customFields: Map<String, String> = emptyMap(),
    ): BotbyeResponse {
        if (botbyeConfig.serverKey.isBlank()) error("[BotBye] server key is not specified")

        val body = BotbyeRequest(
            serverKey = botbyeConfig.serverKey,
            headers = headers,
            requestInfo = connectionDetails,
            customFields = customFields,
        )

        val request = Request.Builder()
            .url("${botbyeConfig.botbyeEndpoint}${botbyeConfig.path}?${token ?: ""}")
            .post(
                writer.writeValueAsString(body).toRequestBody(botbyeConfig.contentType),
            )
            .header("Module-Name", botbyeConfig.moduleName)
            .header("Module-Version", botbyeConfig.moduleVersion)
            .build()

        val response = try {
            handleResponse(
                response = client.sendRequest(request),
            )
        } catch (e: Exception) {
            LOGGER.warning("[BotBye] exception occurred: ${e.message}")

            BotbyeResponse(error = BotbyeError(e.message ?: "[BotBye] failed to sendRequest"))
        }

        return response
    }
}

suspend fun OkHttpClient.sendRequest(request: Request): Response {
    return suspendCoroutine { continuation ->
        newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            },
        )
    }
}
