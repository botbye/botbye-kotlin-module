package com.botbye.model.phishing

import com.botbye.model.common.BotbyeError

data class BotbyePhishingResponse(
    val status: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = byteArrayOf(),
    val error: BotbyeError? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BotbyePhishingResponse

        if (status != other.status) return false
        if (headers != other.headers) return false
        if (!body.contentEquals(other.body)) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + headers.hashCode()
        result = 31 * result + body.contentHashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}
