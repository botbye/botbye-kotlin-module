package com.botbye.model.ato

import com.botbye.service.mapper.Headers
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.Instant

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BotbyeAtoContext(
    val userInfo: BotbyeUserInfo,
    val remoteAddr: String,
    val headers: Headers,
    val eventType: EventType,
    val eventStatus: EventStatus,
    val createdAt: Instant,
    val customFields: Map<String, String>? = null,
) {
    enum class EventType {
        LOGIN,
        REGISTRATION,
        CUSTOM
    }

    enum class EventStatus {
        SUCCESSFUL,
        FAILED,
        PENDING
    }
}
