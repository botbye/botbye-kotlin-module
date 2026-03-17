package com.botbye.model.evaluate

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeIntegrationInfo(
    val moduleName: String? = null,
    val moduleVersion: String? = null,
)
