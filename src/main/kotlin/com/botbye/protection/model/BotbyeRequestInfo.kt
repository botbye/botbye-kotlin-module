package com.botbye.protection.model

import com.botbye.common.http.Headers
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeRequestInfo(
    val ip: String,
    val token: String? = null,
    val headers: Headers,
    val requestMethod: String? = null,
    val requestUri: String? = null,
)
