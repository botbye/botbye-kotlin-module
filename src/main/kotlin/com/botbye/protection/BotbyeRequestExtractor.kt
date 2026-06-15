package com.botbye.protection

import com.botbye.protection.model.BotbyeRequestInfo

/**
 * Maps a framework-specific request object (e.g. Spring's `HttpServletRequest`, Ktor's
 * `ApplicationRequest`) to a [BotbyeRequestInfo]. A framework SDK describes this once via
 * [Botbye.withExtractor]; consumers then pass only their raw request to the `evaluate*` methods.
 */
fun interface BotbyeRequestExtractor<R> {
    fun extract(request: R): BotbyeRequestInfo
}
