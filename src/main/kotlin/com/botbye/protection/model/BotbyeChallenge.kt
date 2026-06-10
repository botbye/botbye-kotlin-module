package com.botbye.protection.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class BotbyeChallenge(
    val type: String,
    val token: String? = null,
)
