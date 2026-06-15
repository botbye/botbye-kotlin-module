package com.botbye.common

/**
 * Best-effort extraction of the client IP from request headers. Prefers the first hop of `x-forwarded-for`, then falls back to `x-real-ip`.
 * Lookup is case-insensitive. Returns `null` when neither header is present.
 */
fun getIpFromHeaders(headers: Map<String, String>): String? {
    val lookup = headers.entries.associate { (k, v) -> k.lowercase() to v }

    lookup["x-forwarded-for"]
        ?.substringBefore(',')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    return lookup["x-real-ip"]?.trim()?.takeIf { it.isNotEmpty() }
}
