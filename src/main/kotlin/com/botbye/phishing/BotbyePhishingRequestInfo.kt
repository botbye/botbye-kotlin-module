package com.botbye.phishing

data class BotbyePhishingRequestInfo(
    val origin: String?,
    val referer: String? = null,
    val query: Map<String, List<String>> = emptyMap(),
)
