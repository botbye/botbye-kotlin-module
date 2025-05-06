package com.botbye.model.ato

import com.botbye.model.common.BotbyeError
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class BotbyeAtoResult(
    val decision: Decision = Decision.ALLOW,
){
    enum class Decision {
        ALLOW,
        BLOCK,
        MFA,
        CHALLENGE,
        IN_PROGRESS
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class BotbyeAtoResponse(
    val result: BotbyeAtoResult? = BotbyeAtoResult(),
    val error: BotbyeError? = null,
)