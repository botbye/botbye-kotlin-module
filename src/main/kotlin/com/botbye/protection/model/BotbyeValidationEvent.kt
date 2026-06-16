package com.botbye.protection.model

import com.botbye.common.ModuleInfo
import com.botbye.common.http.Headers
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
    val customFields: Map<String, String> = emptyMap(),
) : BotbyeEvent {
    companion object {
        private val INTEGRATION = BotbyeIntegrationInfo(
            moduleName = ModuleInfo.NAME,
            moduleVersion = ModuleInfo.VERSION,
        )

        operator fun invoke(
            ip: String,
            token: String,
            headers: Headers,
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
