package com.botbye.model.evaluate

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeEventInfo(
    val type: String,
    val status: BotbyeEventStatus,
)
