package com.botbye.phishing

sealed class BotbyePhishingCatcher(internal val format: String) {

    data object Png : BotbyePhishingCatcher("png")

    data class Svg(
        val innerPngUrl: String,
        val skipExecution: Boolean = true,
    ) : BotbyePhishingCatcher("svg") {
        init {
            require(innerPngUrl.isNotBlank()) {
                "[BotBye] BotbyePhishingCatcher.Svg: innerPngUrl must be a non-blank absolute http(s) URL"
            }
        }
    }
}
