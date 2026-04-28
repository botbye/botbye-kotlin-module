package com.botbye.model.evaluate

import com.botbye.model.common.BotbyeConfig
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonAppend
import com.fasterxml.jackson.databind.annotation.JsonNaming

/**
 * Combined Level 1+2: Bot validation + risk evaluation in a single call.
 * Use when there is no separate proxy — the middleware validates the token
 * and evaluates ATO/abuse risk in one request.
 * [config.bypassBotValidation] is always `false`.
 */
@JsonAppend(attrs = [JsonAppend.Attr("server_key")])
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeFullEvent(
    val request: BotbyeRequestInfo,
    val event: BotbyeEventInfo,
    val user: BotbyeUserInfo,
    val config: BotbyeEvaluateConfig = BotbyeEvaluateConfig(),
    val customFields: Map<String, String> = emptyMap(),
) : BotbyeEvent {
    companion object {
        private val INTEGRATION = BotbyeIntegrationInfo(
            moduleName = BotbyeConfig.MODULE_NAME,
            moduleVersion = BotbyeConfig.MODULE_VERSION,
        )

        operator fun invoke(
            ip: String,
            token: String,
            headers: Map<String, String>,
            user: BotbyeUserInfo,
            eventType: String,
            eventStatus: BotbyeEventStatus,
            requestMethod: String? = null,
            requestUri: String? = null,
            customFields: Map<String, String> = emptyMap(),
        ) = BotbyeFullEvent(
            request = BotbyeRequestInfo(
                ip = ip,
                token = token,
                headers = headers,
                requestMethod = requestMethod,
                requestUri = requestUri,
            ),
            event = BotbyeEventInfo(type = eventType, status = eventStatus),
            user = user,
            customFields = customFields,
        )
    }

    @field:JsonProperty("integration")
    val integration: BotbyeIntegrationInfo = INTEGRATION

    override val urlToken: String? get() = request.token
}
