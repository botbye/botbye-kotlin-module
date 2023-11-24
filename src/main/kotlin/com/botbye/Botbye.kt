package com.botbye

import com.botbye.model.BotbyeConfig
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val LOGGER = Logger.getLogger(Botbye::class.java.getName())

object Botbye {

    private var botbyeConfig: BotbyeConfig = BotbyeConfig()

    private val writer: ObjectWriter = ObjectMapper().registerModule(
        SimpleModule().addSerializer(Headers::class.java, HeadersSerializer()),
    ).writer()

    private val reader: ObjectReader = ObjectMapper().reader()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(
            ConnectionPool(
                maxIdleConnections = botbyeConfig.connectionPoolSize,
                keepAliveDuration = botbyeConfig.keepAliveDuration,
                timeUnit = botbyeConfig.keepAliveDurationTimeUnit,
            ),
        )
        .connectTimeout(
            timeout = botbyeConfig.connectionTimeout,
            unit = TimeUnit.SECONDS,
        )
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
        token: String,
        connectionDetails: ConnectionDetails,
        headers: Headers,
        customFields: List<String> = emptyList(),
    ): BotbyeResponse {
        if (botbyeConfig.serverKey.isBlank()) error("[BotBye] server key is not specified")

        val body = BotbyeRequest(
            token = token,
            serverKey = botbyeConfig.serverKey,
            headers = headers,
            requestInfo = connectionDetails,
            customFields = customFields,
        )

        val request = Request.Builder()
            .url("${botbyeConfig.botbyeEndpoint}${botbyeConfig.path}")
            .post(
                writer.writeValueAsString(body).toRequestBody(botbyeConfig.contentType),
            )
            .addHeader("Module-Name", botbyeConfig.moduleName)
            .addHeader("Module-Version", botbyeConfig.moduleVersion)
            .build()

        val response = client.sendRequest(request)

        return response?.let { handleResponse(it) } ?: BotbyeResponse()
    }
}

suspend fun OkHttpClient.sendRequest(request: Request): Response? {
    return try {
        suspendCoroutine { continuation ->
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
    } catch (e: Exception) {
        LOGGER.warning(e.message)
        null
    }
}
