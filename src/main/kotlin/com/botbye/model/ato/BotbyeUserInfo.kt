package com.botbye.model.ato

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeUserInfo(
    val accountId: String,
    val username: String? = null,
    val email: String? = null,
    val phone: String? = null,
)