package com.botbye.protection

import com.botbye.protection.model.BotbyeEvaluateResponse
import com.botbye.protection.model.BotbyeEvent

/**
 * Explicit-event evaluation surface, independent of any framework request type.
 *
 * Consumers that build [BotbyeEvent]s themselves (rather than relying on a [BotbyeRequestExtractor])
 * should depend on this interface instead of `Botbye<Nothing>` — it carries only the members that do
 * not involve the request type parameter `R`, so there is no `Nothing` to spell out at call sites.
 *
 * [Botbye] implements it for every `R`; [Botbye] `(config)` returns an instance usable as a [BotbyeEvaluator].
 */
interface BotbyeEvaluator {
    /** Send a fully-built event for risk evaluation. Fails open: returns ALLOW + error on failure. */
    suspend fun evaluate(event: BotbyeEvent): BotbyeEvaluateResponse

    /** Replace the runtime configuration (endpoint / server key) of this client. */
    fun setConf(config: BotbyeConfig)
}
