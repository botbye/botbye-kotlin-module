package com.botbye.protection.model

import com.botbye.common.BotbyeError
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
    val signals: Set<String>? = null,
    val scores: Map<String, Double>? = null,
    val challenge: BotbyeChallenge? = null,
    val extraData: BotbyeExtraData? = null,
    val error: BotbyeError? = null,
    val botbyeResult: String? = null,
) {
    @get:JsonIgnore
    val isBlocked: Boolean get() = decision == BotbyeDecision.BLOCK
}
