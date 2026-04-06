package com.botbye.model.evaluate

import com.botbye.model.common.BotbyeConfig
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonAppend
import com.fasterxml.jackson.databind.annotation.JsonNaming

/**
 * Level 1: Bot validation (proxy, pre-authentication).
 * Validates device token and returns bot score. No user context — only bot detection.
 */
@JsonAppend(attrs = [JsonAppend.Attr("server_key")])
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeValidationEvent(
    val request: BotbyeRequestInfo,
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
            requestMethod: String? = null,
            requestUri: String,
            customFields: Map<String, String> = emptyMap(),
        ) = BotbyeValidationEvent(
            request = BotbyeRequestInfo(
                ip = ip,
                token = token,
                headers = headers,
                requestMethod = requestMethod,
                requestUri = requestUri,
            ),
            customFields = customFields,
        )
    }

    @field:JsonProperty("integration")
    val integration: BotbyeIntegrationInfo = INTEGRATION

    override val urlToken: String? get() = request.token
}
