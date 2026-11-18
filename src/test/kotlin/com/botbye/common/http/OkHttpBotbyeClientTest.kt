package com.botbye.common.http

import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkHttpBotbyeClientTest {
    private val server = MockWebServer().apply {
        protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
        start()
    }

    /** The evaluate client's own settings, over cleartext HTTP/2 so the server can reset a stream. */
    private val client = OkHttpBotbyeClient(
        OkHttpClientFactory().createClient(
            maxRequests = 10,
            maxRequestsPerHost = 10,
            maxIdleConnections = 1,
            keepAliveDuration = Duration.ofSeconds(30),
            readTimeout = Duration.ofSeconds(2),
            writeTimeout = Duration.ofSeconds(2),
            connectionTimeout = Duration.ofSeconds(2),
            callTimeout = Duration.ofSeconds(5),
        ).newBuilder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build(),
    )

    @AfterTest
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    private fun resetStream(errorCode: ErrorCode): MockResponse =
        MockResponse().setSocketPolicy(SocketPolicy.RESET_STREAM_AT_START).setHttp2ErrorCode(errorCode.httpCode)

    private fun evaluate(): BotbyeHttpResponse = runBlocking {
        client.call(BotbyeHttpRequest(url = server.url("/api/v1/protect/evaluate").toString(), method = "POST", body = "{}".toByteArray(), contentType = "application/json"))
    }

    @Test
    fun `a refused stream is sent once more with the same body`() {
        server.enqueue(resetStream(ErrorCode.REFUSED_STREAM))
        server.enqueue(MockResponse().setBody("ok"))

        val response = evaluate()

        assertEquals(200, response.status)
        assertEquals("ok", response.body.decodeToString())
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertEquals("{}", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `a stream refused twice fails after one retry`() {
        server.enqueue(resetStream(ErrorCode.REFUSED_STREAM))
        server.enqueue(resetStream(ErrorCode.REFUSED_STREAM))
        server.enqueue(MockResponse().setBody("not reached"))

        val e = assertFailsWith<StreamResetException> { evaluate() }

        assertEquals(ErrorCode.REFUSED_STREAM, e.errorCode)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a stream reset for another reason is not retried`() {
        server.enqueue(resetStream(ErrorCode.INTERNAL_ERROR))
        server.enqueue(MockResponse().setBody("not reached"))

        val e = assertFailsWith<StreamResetException> { evaluate() }

        assertEquals(ErrorCode.INTERNAL_ERROR, e.errorCode)
        assertEquals(1, server.requestCount)
    }
}
