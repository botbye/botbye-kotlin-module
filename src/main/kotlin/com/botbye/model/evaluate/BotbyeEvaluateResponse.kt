package com.botbye.model.evaluate

import com.botbye.model.common.BotbyeError
import com.botbye.model.common.BotbyeExtraData
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeEvaluateResponse(
    val requestId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val decision: BotbyeDecision = BotbyeDecision.ALLOW,
    val riskScore: Double = 0.0,
    val signals: List<String> = emptyList(),
    val scores: Map<String, Double> = emptyMap(),
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val challenge: BotbyeChallenge? = null,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val extraData: BotbyeExtraData? = null,
    val error: BotbyeError? = null,
) {
    val isBlocked: Boolean get() = decision == BotbyeDecision.BLOCK
}
