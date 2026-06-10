package com.botbye.protection.model

import com.botbye.protection.BotbyeConfig
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonAppend
import com.fasterxml.jackson.databind.annotation.JsonNaming

/**
 * Level 2: Risk evaluation (middleware, post-authentication).
 * Evaluates ATO/abuse risk using user context and dynamic metrics.
 * Bot score comes from Level 1 result ([botbyeResult]).
 */
@JsonAppend(attrs = [JsonAppend.Attr("server_key")])
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeRiskScoringEvent(
    val request: BotbyeRequestInfo,
    val event: BotbyeEventInfo,
    val user: BotbyeUserInfo,
    val botbyeResult: String? = null,
    val customFields: Map<String, String> = emptyMap(),
) : BotbyeEvent {
    companion object {
        private val INTEGRATION = BotbyeIntegrationInfo(
            moduleName = BotbyeConfig.MODULE_NAME,
            moduleVersion = BotbyeConfig.MODULE_VERSION,
        )

        operator fun invoke(
            ip: String,
            headers: Map<String, String>,
            user: BotbyeUserInfo,
            eventType: String,
            eventStatus: BotbyeEventStatus,
            botbyeResult: String? = null,
            customFields: Map<String, String> = emptyMap(),
        ): BotbyeRiskScoringEvent {
            val hasResult = !botbyeResult.isNullOrBlank()

            return BotbyeRiskScoringEvent(
                request = BotbyeRequestInfo(ip = ip, headers = headers),
                event = BotbyeEventInfo(type = eventType, status = eventStatus),
                user = user,
                botbyeResult = if (hasResult) botbyeResult else null,
                customFields = customFields,
            )
        }
    }

    @field:JsonProperty("integration")
    val integration: BotbyeIntegrationInfo = INTEGRATION

    override val urlToken: String? get() = null
}
