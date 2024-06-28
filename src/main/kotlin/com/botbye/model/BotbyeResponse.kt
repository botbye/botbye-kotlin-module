package com.botbye.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class BotbyeError(
    @JsonProperty("message")
    val message: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BotbyeChallengeResult(
    @get:JsonProperty("isAllowed")
    @JsonProperty("isAllowed")
    val isAllowed: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BotbyeResponse(
    val result: BotbyeChallengeResult? = BotbyeChallengeResult(),
    val reqId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val error: BotbyeError? = null,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val extraData: BotbyeExtraData? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class BotbyeExtraData(
    /**
     * The IP address of the user. If a proxy is used, this will be the proxy IP address.
     */
    val ip: String? = null,
    val asn: String? = null,
    val country: String? = null,
    val browser: String? = null,
    val browserVersion: String? = null,
    val deviceName: String? = null, // Galaxy S9
    val deviceType: String? = null, // Mobile Phone
    val deviceCodeName: String? = null, // SM-G960F
    val platform: String? = null, // Android
    val platformVersion: String? = null, // 8
    /**
     * The real IP address of the user. If a proxy is used, this will be the real IP address, otherwise, it will be the same as `ip`.
     * Note: This does not apply to VPN usage.
     */
    val realIp: String? = null,
    val realCountry: String? = null,
)
