package com.botbye.model.evaluate

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeRequestInfo(
    val ip: String,
    val token: String? = null,
    val headers: Map<String, String>,
    val requestMethod: String? = null,
    val requestUri: String? = null,
)
