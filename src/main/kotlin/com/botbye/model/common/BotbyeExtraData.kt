package com.botbye.model.common

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

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