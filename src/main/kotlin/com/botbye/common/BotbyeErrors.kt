package com.botbye.common

/**
 * Normalized error messages surfaced in [BotbyeError.message] when an evaluation falls back open.
 * Mirror the BotBye node-core error codes so messages are consistent across SDKs.
 */
object BotbyeErrors {
    const val SDK_ERROR = "SDK error"
    const val UNKNOWN_ERROR = "unknown error"
    const val TIMEOUT_ERROR = "timeout"
    const val CONNECTION_ERROR = "connection error"
    const val JSON_ERROR = "invalid json response"
}
