package com.botbye.service.httpclient

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface RestClient {
    suspend fun sendRequest(request: Request): Response
}

class OkHttpRestClient(
    private val client: OkHttpClient,
) : RestClient {
    override suspend fun sendRequest(request: Request): Response {
        return suspendCoroutine { continuation ->
            client.newCall(request).enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                }
            )
        }
    }
}