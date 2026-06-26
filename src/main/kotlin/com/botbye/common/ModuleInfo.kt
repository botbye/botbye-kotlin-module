package com.botbye.common

/**
 * Identity of this SDK build. Sent on every BotBye request via the Module-Name / Module-Version
 * headers and embedded in evaluate event payloads. Shared by the protection and phishing clients,
 * so it lives in [com.botbye.common] rather than in either client's config.
 */
object ModuleInfo {
    const val NAME = "Kotlin"
    const val VERSION = "3.0.1"
}
