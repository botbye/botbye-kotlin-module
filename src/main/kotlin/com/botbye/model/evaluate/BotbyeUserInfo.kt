package com.botbye.model.evaluate

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeUserInfo(
    val accountId: String,
    val username: String? = null,
    val email: String? = null,
    val phone: String? = null,
)
