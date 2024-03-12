package com.botbye.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class BotbyeError(
    @JsonProperty("message")
    val message: String,
)

data class BotbyeChallengeResult(
    @JsonProperty("isAllowed")
    val isAllowed: Boolean = true,
)

data class BotbyeResponse(
    val result: BotbyeChallengeResult? = BotbyeChallengeResult(),
    val reqId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val error: BotbyeError? = null,
)
