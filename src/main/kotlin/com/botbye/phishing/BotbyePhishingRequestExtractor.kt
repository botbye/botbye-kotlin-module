package com.botbye.phishing

/**
 * Extracts the `Origin` header value from a framework-specific request object (e.g. Spring's
 * `HttpServletRequest`, Ktor's `ApplicationRequest`). A framework SDK describes this once via
 * [BotbyePhishingClient.withExtractor]; consumers then pass only their raw request to
 * [BotbyePhishingClient.fetchImage]. Return `null` when no `Origin` is present.
 */
fun interface BotbyePhishingRequestExtractor<R> {
    fun extractOrigin(request: R): String?
}
