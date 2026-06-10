package com.botbye.common

import com.fasterxml.jackson.core.JsonProcessingException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

object ErrorClassifier {
    fun classify(e: Throwable): String = when {
        e is SocketTimeoutException -> "timeout"
        e is ConnectException -> "connection error"
        e is JsonProcessingException -> "invalid json response"
        e is IOException -> "connection error"
        e.message?.startsWith("connection error") == true -> "connection error"
        else -> e.message ?: "unknown error"
    }
}
