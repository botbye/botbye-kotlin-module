package com.botbye.model.validator

import com.botbye.model.common.BotbyeError
import com.botbye.model.common.BotbyeExtraData
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class BotbyeChallengeResult(
    @get:JsonProperty("isAllowed")
    @JsonProperty("isAllowed")
    val isAllowed: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BotbyeValidatorResponse(
    val result: BotbyeChallengeResult? = BotbyeChallengeResult(),
    val reqId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val error: BotbyeError? = null,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val extraData: BotbyeExtraData? = null,
)

