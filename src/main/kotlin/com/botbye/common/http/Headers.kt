package com.botbye.common.http

data class Headers(val headers: Map<String, List<String>>) {
    fun toFlatMap(): Map<String, String> =
        buildMap(headers.size) {
            headers.forEach { (k, v) -> put(k.lowercase(), v.joinToString()) }
        }
}
