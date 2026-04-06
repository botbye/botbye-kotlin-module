package com.botbye.model.evaluate

import com.botbye.model.common.BotbyeConfig
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonAppend
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonAppend(attrs = [JsonAppend.Attr("server_key")])
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BotbyeEvaluateRequest private constructor(
    val requestId: String? = null,
    val botbyeResult: String? = null,
    val event: BotbyeEventInfo? = null,
    val user: BotbyeUserInfo? = null,
    val request: BotbyeRequestInfo,
    val config: BotbyeEvaluateConfig = BotbyeEvaluateConfig(),
    val customFields: Map<String, String> = emptyMap(),
) {
    @field:JsonProperty("integration")
    val integration: BotbyeIntegrationInfo = INTEGRATION

    companion object {
        private val INTEGRATION = BotbyeIntegrationInfo(
            moduleName = BotbyeConfig.MODULE_NAME,
            moduleVersion = BotbyeConfig.MODULE_VERSION,
        )

        /**
         * Level 1: Bot validation (proxy, pre-authentication).
         * Validates device token and returns bot score.
         * No user context — only bot detection.
         */
        fun botValidation(
            ip: String,
            token: String,
            headers: Map<String, String>,
            requestMethod: String? = null,
            requestUri: String,
            customFields: Map<String, String> = emptyMap(),
        ) = BotbyeEvaluateRequest(
            request = BotbyeRequestInfo(
                ip = ip,
                token = token,
                headers = headers,
                requestMethod = requestMethod,
                requestUri = requestUri,
            ),
            customFields = customFields,
        )

        /**
         * Level 2: Risk evaluation (middleware, post-authentication).
         * Evaluates ATO/abuse risk using user context and dynamic metrics.
         * Bot score comes from Level 1 result (botbyeResult).
         */
        fun riskEvaluation(
            ip: String,
            headers: Map<String, String>,
            user: BotbyeUserInfo,
            eventType: String,
            eventStatus: BotbyeEventStatus,
            botbyeResult: String? = null,
            customFields: Map<String, String> = emptyMap(),
        ): BotbyeEvaluateRequest {
            val hasResult = !botbyeResult.isNullOrBlank()

            return BotbyeEvaluateRequest(
                botbyeResult = if (hasResult) botbyeResult else null,
                event = BotbyeEventInfo(type = eventType, status = eventStatus),
                user = user,
                request = BotbyeRequestInfo(ip = ip, headers = headers),
                config = if (hasResult) {
                    BotbyeEvaluateConfig()
                } else {
                    BotbyeEvaluateConfig(bypassBotValidation = true)
                },
                customFields = customFields,
            )
        }

        /**
         * Combined Level 1+2: Bot validation + risk evaluation in a single call.
         * Use when there is no separate proxy — the middleware validates the token
         * and evaluates ATO/abuse risk in one request.
         */
        fun fullEvaluation(
            ip: String,
            token: String,
            headers: Map<String, String>,
            user: BotbyeUserInfo,
            eventType: String,
            eventStatus: BotbyeEventStatus,
            requestMethod: String? = null,
            requestUri: String? = null,
            customFields: Map<String, String> = emptyMap(),
        ) = BotbyeEvaluateRequest(
            event = BotbyeEventInfo(type = eventType, status = eventStatus),
            user = user,
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
}
