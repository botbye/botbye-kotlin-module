package com.botbye.model.validator

import com.fasterxml.jackson.annotation.JsonProperty

data class ConnectionDetails(
    @get:JsonProperty("remote_addr")
    val remoteAddr: String,
    @get:JsonProperty("request_method")
    val requestMethod: String,
    @get:JsonProperty("request_uri")
    val requestUri: String,
)