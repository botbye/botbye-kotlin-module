package com.botbye.phishing

data class BotbyePhishingConfig(
    val endpoint: String = "https://verify.botbye.com",
    val clientKey: String = "",
) {

    init {
        require(endpoint.isNotBlank()) { "[BotBye] phishing endpoint is not specified" }
        require(clientKey.isNotBlank()) { "[BotBye] phishing clientKey is not specified" }
    }
}
