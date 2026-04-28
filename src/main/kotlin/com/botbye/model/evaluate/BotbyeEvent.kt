package com.botbye.model.evaluate

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = BotbyeValidationEvent::class, name = "validate"),
    JsonSubTypes.Type(value = BotbyeRiskScoringEvent::class, name = "risk"),
    JsonSubTypes.Type(value = BotbyeFullEvent::class, name = "full"),
)
sealed interface BotbyeEvent {
    /** Token to append as URL query param (null for risk-only requests) */
    val urlToken: String?
}
