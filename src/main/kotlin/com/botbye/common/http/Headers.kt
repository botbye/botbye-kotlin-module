package com.botbye.common.http

/**
 * Multi-value HTTP header carrier. Framework adapters wrap their native (multi-value) headers in this
 * type; the SDK owns the multi-value → flat-string normalization (lowercased keys, comma-joined
 * values) at serialization time via `HeadersSerializer`, so adapters never flatten themselves.
 */
data class Headers(val headers: Map<String, List<String>>)
