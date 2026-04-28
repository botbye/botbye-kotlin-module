# BotBye Kotlin Module

Kotlin SDK for the [BotBye](https://botbye.com) Unified Protection Platform — unifying fraud prevention and real-time event monitoring in one platform.

BotBye goes beyond fixed bot/ATO checks. Risk dimensions and metrics are fully dynamic — you define what to measure and what rules to apply per project. This means the same platform covers bot detection, account takeover, multi-accounting, payment fraud, promotion abuse, or any custom fraud scenario specific to your business.

## Requirements

- JDK 17 or higher
- Gradle or Maven

## Installation

### Gradle (Kotlin DSL)

```kotlin
implementation("com.botbye:kotlin-module:2.0.0")
```

### Gradle (Groovy DSL)

```groovy
implementation 'com.botbye:kotlin-module:2.0.0'
```

### Maven

```xml
<dependency>
    <groupId>com.botbye</groupId>
    <artifactId>kotlin-module</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Overview

The SDK provides three request types for different integration levels:

| Request Type | Use Case | Where It Runs |
|---|---|---|
| `BotbyeValidationEvent` | **Level 1** — Bot filtering | Proxy or middleware, before user identity is known |
| `BotbyeRiskScoringEvent` | **Level 2** — Risk scoring & event logging | Application layer, when user identity is known |
| `BotbyeFullEvent` | **Level 1+2 combined** | Application layer when no separate proxy exists |

All requests go to a single endpoint (`POST /api/v1/protect/evaluate`) and return a unified response with a decision (`ALLOW`, `CHALLENGE`, `BLOCK`), risk scores per dimension, and triggered signals. Dimensions are dynamic — the platform ships with built-in ones (`bot`, `ato`, `abuse`) but you can define custom dimensions (e.g., `payment_fraud`, `promotion_abuse`) per project without code changes.

Every evaluation call is also recorded as a **protection event** — logged to the analytics pipeline and used to compute real-time metrics that feed the rules engine. Metrics are fully configurable per project: the platform ships with built-in ones (failed logins, distinct IPs per account, device reuse, etc.) and you can define custom metrics for your specific use case (e.g., "failed transactions over $1000 per account in 1 hour"). This means `BotbyeRiskScoringEvent` serves a dual purpose: it both evaluates risk **and** logs the event for future analysis and metric aggregation.

## Quick Start

### 1. Initialize the Client

```kotlin
import com.botbye.Botbye
import com.botbye.model.common.BotbyeConfig

val config = BotbyeConfig(
    serverKey = "your-server-key" // from https://app.botbye.com
)

val botbye = Botbye(config)
```

### 2. Bot Validation (Level 1)

Validate device tokens where user identity is not yet available — at the proxy layer or in a middleware before authentication.

```kotlin
import com.botbye.model.evaluate.BotbyeValidationEvent

val response = botbye.evaluate(BotbyeValidationEvent(
    ip = request.remoteAddr,
    token = request.getParameter("botbye_token") ?: "", // extract the token from wherever you pass it: query param, header, body, etc.
    headers = flattenHeaders(request),
    requestMethod = request.method,
    requestUri = request.requestURI,
))

if (response.isBlocked) {
    return ResponseEntity.status(403).body("Access denied")
}

// Propagate bot score to Level 2 via header
httpResponse.setHeader(Botbye.RESULT_HEADER, botbye.encodeResult(response))
```

### 3. Risk Scoring & Event Logging (Level 2)

Evaluate risk and log events when user identity is known. Each call both scores the request **and** feeds the real-time metrics engine, so you should call `evaluate()` for every significant user action — not just when you need a decision.

```kotlin
import com.botbye.model.evaluate.BotbyeRiskScoringEvent
import com.botbye.model.evaluate.BotbyeUserInfo
import com.botbye.model.evaluate.BotbyeEventStatus
import com.botbye.model.evaluate.BotbyeDecision

val response = botbye.evaluate(BotbyeRiskScoringEvent(
    ip = request.remoteAddr,
    headers = flattenHeaders(request),
    user = BotbyeUserInfo(
        accountId = userId,
        email = userEmail,       // optional
        phone = userPhone,       // optional
    ),
    eventType = "LOGIN",
    eventStatus = BotbyeEventStatus.SUCCESSFUL,
    botbyeResult = request.getHeader("X-Botbye-Result"), // from Level 1
))

when (response.decision) {
    BotbyeDecision.BLOCK     -> return ResponseEntity.status(403).build()
    BotbyeDecision.CHALLENGE -> return showChallenge(response.challenge)
    BotbyeDecision.ALLOW     -> continueRequest()
}
```

When `botbyeResult` is `null` (no Level 1 upstream), bot validation is automatically bypassed.

#### Event Types

`eventType` is an arbitrary string — the server accepts any value. Pass any string that matches your business domain:

```kotlin
"LOGIN"
"REGISTRATION"
"TRANSACTION"
"BONUS_CLAIM"
"PASSWORD_RESET"
"WITHDRAWAL"
```

#### Using Level 2 for Event Logging

Even when you don't need to act on the decision, sending events builds the metrics profile for the account. This enables rules like "more than 5 failed logins in 10 minutes" or "distinct devices per account in 1 hour":

```kotlin
// Log a failed login attempt — feeds metrics even if you don't act on the decision
botbye.evaluate(BotbyeRiskScoringEvent(
    ip = request.remoteAddr,
    headers = flattenHeaders(request),
    user = BotbyeUserInfo(accountId = userId),
    eventType = "LOGIN",
    eventStatus = BotbyeEventStatus.FAILED,
))

// Log a custom business event
botbye.evaluate(BotbyeRiskScoringEvent(
    ip = request.remoteAddr,
    headers = flattenHeaders(request),
    user = BotbyeUserInfo(accountId = userId),
    eventType = "BONUS_CLAIM",
    eventStatus = BotbyeEventStatus.SUCCESSFUL,
    customFields = mapOf("bonus_id" to "welcome_100"),
))
```

### 4. Full Evaluation (Level 1+2 Combined)

Use when there is no separate proxy layer — validates the device token and evaluates risk in a single call.

```kotlin
import com.botbye.model.evaluate.BotbyeFullEvent

val response = botbye.evaluate(BotbyeFullEvent(
    ip = request.remoteAddr,
    token = request.getParameter("botbye_token") ?: "",
    headers = flattenHeaders(request),
    user = BotbyeUserInfo(accountId = userId),
    eventType = "LOGIN",
    eventStatus = BotbyeEventStatus.FAILED,
))
```

## Response

`BotbyeEvaluateResponse` contains:

| Field | Type | Description |
|---|---|---|
| `requestId` | `UUID?` | Request UUID |
| `decision` | `BotbyeDecision` | `ALLOW`, `CHALLENGE`, or `BLOCK` |
| `riskScore` | `Double?` | Overall risk score (0–1) |
| `scores` | `Map<String, Double>?` | Per-dimension scores (`bot`, `ato`, `abuse`, ...) |
| `signals` | `List<String>?` | Triggered signal names (e.g., `BruteForce`, `ImpossibleTravel`) |
| `challenge` | `BotbyeChallenge?` | Challenge type and token (when decision is `CHALLENGE`) |
| `extraData` | `BotbyeExtraData?` | Enriched device data (IP, country, browser, device, etc.) |
| `config` | `BotbyeEvaluateConfig` | Config flags (`bypassBotValidation`) |
| `error` | `BotbyeError?` | Error details (on fallback) |

```kotlin
response.decision              // BotbyeDecision.ALLOW
response.isBlocked             // false
response.riskScore             // 0.72
response.scores                // {bot=0.15, ato=0.72, abuse=0.05}
response.signals               // [BruteForce, ImpossibleTravel]
response.challenge?.type       // "captcha"
response.extraData?.country    // "US"
```

## Level 1 to Level 2 Propagation

When using both levels, propagate the Level 1 result to Level 2 via the `X-Botbye-Result` header. This allows the platform to link both evaluations by `requestId` and combine bot score from Level 1 with risk scores from Level 2 into a single unified result:

```kotlin
// Level 1 (proxy) — validate and forward result
val response = botbye.evaluate(BotbyeValidationEvent(...))
httpResponse.setHeader(Botbye.RESULT_HEADER, botbye.encodeResult(response))

// Or bypass validation entirely
httpResponse.setHeader(Botbye.RESULT_HEADER, botbye.bypassResult())

// Level 2 (middleware) — pass the header value as botbyeResult
val response = botbye.evaluate(BotbyeRiskScoringEvent(
    // ...
    botbyeResult = request.getHeader("X-Botbye-Result"),
))
```

## Configuration

```kotlin
val config = BotbyeConfig(
    serverKey = "your-server-key",                         // from https://app.botbye.com
    botbyeEndpoint = "https://verify.botbye.com",          // default
    readTimeout = Duration.ofSeconds(2),                   // default
    writeTimeout = Duration.ofSeconds(2),                  // default
    connectionTimeout = Duration.ofSeconds(2),             // default
    callTimeout = Duration.ofSeconds(5),                   // default
    maxIdleConnections = 250,                              // default
    keepAliveDuration = Duration.ofSeconds(300),            // default
    maxRequestsPerHost = 1500,                             // default
    maxRequests = 1500,                                    // default
)
```

## Error Handling

The SDK follows a **fail-open** strategy. On network or server errors, `evaluate()` returns a bypass response (`BotbyeDecision.ALLOW` with `bypassBotValidation = true`) instead of throwing:

```kotlin
val response = botbye.evaluate(event)

if (response.error != null) {
    // Evaluation failed, request was allowed by default
    logger.warn(response.error.message)
}
```

## Framework Integration

### Spring WebFlux (CoWebFilter)

```kotlin
import com.botbye.Botbye
import com.botbye.model.evaluate.BotbyeValidationEvent
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange

@Component
class BotbyeFilter(private val botbye: Botbye) : CoWebFilter() {

    override suspend fun filter(exchange: ServerWebExchange, chain: CoWebFilterChain) {
        val request = exchange.request
        val headers = request.headers.toSingleValueMap()

        val result = botbye.evaluate(BotbyeValidationEvent(
            ip = request.remoteAddress?.address?.hostAddress ?: "",
            token = request.queryParams.getFirst("botbye_token") ?: "",
            headers = headers,
            requestMethod = request.method.name(),
            requestUri = request.uri.path,
        ))

        if (result.isBlocked) {
            exchange.response.statusCode = HttpStatus.FORBIDDEN
            return
        }

        exchange.response.headers.set(Botbye.RESULT_HEADER, botbye.encodeResult(result))
        chain.filter(exchange)
    }
}
```

### Ktor Plugin

```kotlin
import com.botbye.Botbye
import com.botbye.model.evaluate.BotbyeValidationEvent
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun Application.configureBotbye(botbye: Botbye) {
    intercept(ApplicationCallPipeline.Plugins) {
        val headers = call.request.headers.entries()
            .associate { (k, v) -> k to v.joinToString(", ") }

        val response = botbye.evaluate(BotbyeValidationEvent(
            ip = call.request.local.remoteAddress,
            token = call.request.queryParameters["botbye_token"] ?: "",
            headers = headers,
            requestMethod = call.request.local.method.value,
            requestUri = call.request.local.uri,
        ))

        if (response.isBlocked) {
            call.respond(HttpStatusCode.Forbidden, "Access denied")
            finish()
            return@intercept
        }

        call.response.header(Botbye.RESULT_HEADER, botbye.encodeResult(response))
    }
}
```

## Testing

```bash
./gradlew build
./gradlew test
```

## License

MIT

## Support

For support, visit [botbye.com](https://botbye.com) or contact [accounts@botbye.com](mailto:accounts@botbye.com).
