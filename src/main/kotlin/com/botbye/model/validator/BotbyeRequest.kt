package com.botbye.model.validator

import com.botbye.service.mapper.Headers
import com.fasterxml.jackson.annotation.JsonProperty

data class BotbyeRequest(
    @get:JsonProperty("server_key")
    val serverKey: String,
    val headers: Headers,
    @get:JsonProperty("request_info")
    val requestInfo: ConnectionDetails,
    @get:JsonProperty("custom_fields")
    val customFields: Map<String, String>,
)
