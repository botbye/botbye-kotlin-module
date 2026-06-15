package com.botbye.protection

import com.botbye.common.BotbyeError
import com.botbye.protection.model.BotbyeDecision
import com.botbye.protection.model.BotbyeEvaluateResponse

/**
 * Builds the fail-open response returned when an evaluation cannot complete (network/SDK error).
 * Decision is [BotbyeDecision.ALLOW] and [BotbyeEvaluateResponse.error] carries [message]. Public so
 * callers and framework adapters can produce the same shape for their own short-circuit paths.
 */
fun createFallbackEvaluationResult(message: String): BotbyeEvaluateResponse =
    BotbyeEvaluateResponse(
        decision = BotbyeDecision.ALLOW,
        error = BotbyeError(message),
    )
