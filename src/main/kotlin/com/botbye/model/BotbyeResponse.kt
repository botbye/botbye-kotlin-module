package com.botbye.model

import java.util.UUID

data class BotbyeError(
    val message: String,
)

data class BotbyeChallengeResult(
    val isBot: Boolean = false,
    val banRequired: Boolean = false,
)

data class BotbyeResponse(
    val result: BotbyeChallengeResult? = BotbyeChallengeResult(),
    val reqId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val error: BotbyeError? = null,
)
