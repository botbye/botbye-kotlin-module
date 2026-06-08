package com.botbye.model.phishing

import com.botbye.model.common.normalizeBaseUrl

data class BotbyePhishingConfig(
    var endpoint: String = "https://verify.botbye.com",
    var clientKey: String = "",
) {

    init {
        require(endpoint.isNotBlank()) { "[BotBye] phishing endpoint is not specified" }
        require(clientKey.isNotBlank()) { "[BotBye] phishing clientKey is not specified" }
        endpoint = normalizeBaseUrl(endpoint)
    }
}

