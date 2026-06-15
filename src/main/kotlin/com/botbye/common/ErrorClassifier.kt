package com.botbye.common

import com.fasterxml.jackson.core.JsonProcessingException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/** Maps a thrown exception to one of the normalized [BotbyeErrors] messages. */
object ErrorClassifier {
    fun classify(e: Throwable): String = when {
        e is SocketTimeoutException -> BotbyeErrors.TIMEOUT_ERROR
        e is ConnectException -> BotbyeErrors.CONNECTION_ERROR
        e is JsonProcessingException -> BotbyeErrors.JSON_ERROR
        e is IOException -> BotbyeErrors.CONNECTION_ERROR
        e.message?.startsWith(BotbyeErrors.CONNECTION_ERROR) == true -> BotbyeErrors.CONNECTION_ERROR
        else -> BotbyeErrors.UNKNOWN_ERROR
    }
}
