package com.botbye.model.evaluate

import com.botbye.model.common.BotbyeError
import com.botbye.model.common.BotbyeExtraData
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeEvaluateResponse(
    val requestId: UUID? = null,
    val decision: BotbyeDecision = BotbyeDecision.ALLOW,
    val riskScore: Double? = null,
    val signals: List<String>? = null,
    val scores: Map<String, Double>? = null,
    val config: BotbyeEvaluateConfig = BotbyeEvaluateConfig(),
    val challenge: BotbyeChallenge? = null,
    val extraData: BotbyeExtraData? = null,
    val error: BotbyeError? = null,
) {
    @get:JsonIgnore
    val isBlocked: Boolean get() = decision == BotbyeDecision.BLOCK
}
