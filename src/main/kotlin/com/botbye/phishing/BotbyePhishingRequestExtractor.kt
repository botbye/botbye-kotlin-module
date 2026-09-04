package com.botbye.phishing

/**
 * Maps a framework-specific request object (e.g. Spring's `HttpServletRequest`, Ktor's
 * `ApplicationRequest`) to a [BotbyePhishingRequestInfo]. A framework SDK describes this once via
 * [BotbyePhishingClient.withExtractor]; consumers then pass only their raw request to
 * [BotbyePhishingClient.fetchCatcher].
 */
fun interface BotbyePhishingRequestExtractor<R> {
    fun extract(request: R): BotbyePhishingRequestInfo
}
