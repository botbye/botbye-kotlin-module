package com.botbye.common.http

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import java.io.Closeable
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Transport-neutral HTTP request. Carries everything the SDK needs to issue a call without exposing
 * a concrete HTTP library to the rest of the codebase.
 */
data class BotbyeHttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BotbyeHttpRequest) return false

        if (url != other.url) return false
        if (method != other.method) return false
        if (headers != other.headers) return false
        if (!body.contentEquals(other.body)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}

/**
 * Transport-neutral HTTP response. [body] is fully buffered so the underlying connection is released
 * before the response is handed back.
 */
data class BotbyeHttpResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = byteArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BotbyeHttpResponse) return false

        if (status != other.status) return false
        if (headers != other.headers) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + headers.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

/**
 * Pluggable HTTP transport. The SDK depends only on this interface; swap in a custom implementation
 * (any HTTP stack) by passing it to the [com.botbye.protection.Botbye] /
 * [com.botbye.phishing.BotbyePhishingClient] constructors. The default is [OkHttpBotbyeClient].
 */
interface BotbyeHttpClient : Closeable {
    /** Identifier of the transport implementation, e.g. `"okhttp"`. Sent for diagnostics. */
    val type: String

    suspend fun call(request: BotbyeHttpRequest): BotbyeHttpResponse

    /**
     * Release any transport-owned resources (thread pools, connection pools). Default is a no-op for
     * stateless implementations. Only invoked by a client that owns this transport (i.e. created the
     * default); a transport passed in by the caller is never closed by the SDK.
     */
    override fun close() {}
}

/**
 * Default [BotbyeHttpClient] backed by OkHttp. Uses `suspendCancellableCoroutine` so a cancelled
 * caller coroutine cancels the in-flight OkHttp call instead of leaking a zombie request.
 */
class OkHttpBotbyeClient(
    val client: OkHttpClient,
) : BotbyeHttpClient {
    override val type: String = "okhttp"

    private val callClient: OkHttpClient = client.newBuilder().addInterceptor(RetryRefusedStreamInterceptor).build()

    override suspend fun call(request: BotbyeHttpRequest): BotbyeHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = callClient.newCall(buildRequest(request))

            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }

                    // Body is buffered and the response closed here, on OkHttp's callback thread, before
                    // resuming the caller. No open Response ever escapes this callback, so a caller that is
                    // cancelled mid-flight can never leak a connection — `use` always releases it.
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val mapped = response.use { resp ->
                                BotbyeHttpResponse(
                                    status = resp.code,
                                    headers = buildMap(resp.headers.size) {
                                        for (i in 0 until resp.headers.size) {
                                            put(resp.headers.name(i), resp.headers.value(i))
                                        }
                                    },
                                    body = resp.body?.bytes() ?: byteArrayOf(),
                                )
                            }
                            if (continuation.isActive) {
                                continuation.resume(mapped)
                            }
                        } catch (e: Throwable) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                },
            )
        }

    private fun buildRequest(request: BotbyeHttpRequest): Request {
        val body = request.body?.toRequestBody(request.contentType?.toMediaTypeOrNull())
        val builder = Request.Builder()
            .url(request.url)
            .method(request.method, body)

        request.headers.forEach { (name, value) -> builder.header(name, value) }

        return builder.build()
    }

    /** Shuts down the dispatcher's thread pool and evicts pooled connections so no threads leak. */
    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        client.cache?.close()
    }
}

/**
 * Sends a request once more when the server refused its HTTP/2 stream, which happens to every stream in flight when
 * the server closes the connection with GOAWAY. A refused stream was never processed (RFC 9113 §8.7), so replaying an
 * evaluate cannot spend its token twice; `retryOnConnectionFailure` stays off because it also replays requests the
 * server may already have processed.
 */
private object RetryRefusedStreamInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (e: StreamResetException) {
        if (e.errorCode != ErrorCode.REFUSED_STREAM) throw e

        chain.proceed(chain.request())
    }
}
