package com.botbye.model.phishing

import com.botbye.model.common.normalizeBaseUrl

data class BotbyePhishingConfig(
    var endpoint: String = "",
    var accountId: String = "",
    var projectId: String = "",
    var apiKey: String = "",
) {

    init {
        require(endpoint.isNotBlank()) { "[BotBye] phishing endpoint is not specified" }
        require(accountId.isNotBlank()) { "[BotBye] phishing accountId is not specified" }
        require(projectId.isNotBlank()) { "[BotBye] phishing projectId is not specified" }
        require(apiKey.isNotBlank()) { "[BotBye] phishing apiKey is not specified" }
        endpoint = normalizeBaseUrl(endpoint)
    }
}

