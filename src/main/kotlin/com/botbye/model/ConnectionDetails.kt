package com.botbye.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Date

data class ConnectionDetails(
    @get:JsonProperty("created_at")
    val createdAt: Date = Date(),
    @get:JsonProperty("server_port")
    val serverPort: Int,
    @get:JsonProperty("remote_addr")
    val remoteAddr: String,
    @get:JsonProperty("server_name")
    val serverName: String,
    @get:JsonProperty("request_method")
    val requestMethod: String,
    @get:JsonProperty("request_uri")
    val requestUri: String,
)
