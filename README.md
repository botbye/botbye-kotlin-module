# BotBye Kotlin Module

Kotlin SDK for the [BotBye](https://botbye.com) Unified Protection Platform — unifying fraud prevention and real-time event monitoring in one platform.

BotBye goes beyond fixed bot/ATO checks. Risk dimensions and metrics are fully dynamic — you define what to measure and what rules to apply per project. This means the same platform covers bot detection, account takeover, multi-accounting, payment fraud, promotion abuse, or any custom fraud scenario specific to your business.

## Requirements

- JDK 17 or higher
- Gradle or Maven

## Installation

### Gradle (Kotlin DSL)

```kotlin
implementation("com.botbye:kotlin-module:4.0.0")
```

### Gradle (Groovy DSL)

```groovy
implementation 'com.botbye:kotlin-module:4.0.0'
```

### Maven

```xml
<dependency>
    <groupId>com.botbye</groupId>
    <artifactId>kotlin-module</artifactId>
    <version>4.0.0</version>
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
import com.botbye.protection.Botbye
import com.botbye.protection.BotbyeConfig

val config = BotbyeConfig(
    serverKey = "your-server-key" // from https://app.botbye.com
)

val botbye = Botbye(config)
```

### 2. Bot Validation (Level 1)

Validate device tokens where user identity is not yet available — at the proxy layer or in a middleware before authentication.

Headers are passed as a `com.botbye.common.http.Headers` wrapping your framework's **multi-value** headers (`Map<String, List<String>>`). The SDK owns the normalization (lowercased keys, comma-joined values) at serialization time, so you never flatten them yourself:

```kotlin
import com.botbye.common.http.Headers

// Build a Headers from your framework request once, reuse everywhere.
fun headersOf(req: HttpServletRequest) = Headers(
    req.headerNames.toList().associateWith { req.getHeaders(it).toList() },
)
```

```kotlin
import com.botbye.protection.model.BotbyeValidationEvent

val response = botbye.evaluate(BotbyeValidationEvent(
    ip = request.remoteAddr,
    token = request.getParameter("botbye_token") ?: "", // extract the token from wherever you pass it: query param, header, body, etc.
    headers = headersOf(request),
    requestMethod = request.method,
    requestUri = request.requestURI,
))

if (response.isBlocked) {
    return ResponseEntity.status(403).body("Access denied")
}
```

### 3. Risk Scoring & Event Logging (Level 2)

Evaluate risk and log events when user identity is known. Each call both scores the request **and** feeds the real-time metrics engine, so you should call `evaluate()` for every significant user action — not just when you need a decision.

```kotlin
import com.botbye.protection.model.BotbyeRiskScoringEvent
import com.botbye.protection.model.BotbyeUserInfo
import com.botbye.protection.model.BotbyeEventStatus
import com.botbye.protection.model.BotbyeDecision

val response = botbye.evaluate(BotbyeRiskScoringEvent(
    ip = request.remoteAddr,
    headers = headersOf(request),
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
    headers = headersOf(request),
    user = BotbyeUserInfo(accountId = userId),
    eventType = "LOGIN",
    eventStatus = BotbyeEventStatus.FAILED,
))

// Log a custom business event
botbye.evaluate(BotbyeRiskScoringEvent(
    ip = request.remoteAddr,
    headers = headersOf(request),
    user = BotbyeUserInfo(accountId = userId),
    eventType = "BONUS_CLAIM",
    eventStatus = BotbyeEventStatus.SUCCESSFUL,
    customFields = mapOf("bonus_id" to "welcome_100"),
))
```

### 4. Full Evaluation (Level 1+2 Combined)

Use when there is no separate proxy layer — validates the device token and evaluates risk in a single call.

```kotlin
import com.botbye.protection.model.BotbyeFullEvent

val response = botbye.evaluate(BotbyeFullEvent(
    ip = request.remoteAddr,
    token = request.getParameter("botbye_token") ?: "",
    headers = headersOf(request),
    user = BotbyeUserInfo(accountId = userId),
    eventType = "LOGIN",
    eventStatus = BotbyeEventStatus.FAILED,
))
```

### 5. Phishing Image Tracking

The phishing tracking pixel is embedded on a protected site; when a phishing clone copies the
markup, the pixel is requested with the clone's `Origin` — or, where the pixel is embedded as
`<object data="…svg">` and no `Origin` is sent at all, with its `Referer`. Either header names the
page, which is what lets BotBye record a phishing candidate.

Phishing lives in its own dedicated `BotbyePhishingClient` — **separate from the evaluate `Botbye`
client**. The project is identified by a public, browser-safe `clientKey` in the URL path, so the
client needs **no server key**; you can construct it standalone. On construction it fires a
best-effort server-integration init handshake (`POST /api/v1/phishing/init-request/v1/{clientKey}`)
reporting this module, and `fetchCatcher` proxies the asset via the server `/server` route so
the backend can attribute it to this module even when the browser never reaches BotBye directly.

```kotlin
import com.botbye.phishing.BotbyePhishingCatcher
import com.botbye.phishing.BotbyePhishingClient
import com.botbye.phishing.BotbyePhishingConfig

val phishing = BotbyePhishingClient(
    BotbyePhishingConfig(
        endpoint = "https://verify.botbye.com", // default
        clientKey = "<public-client-key>",
    )
)

// One method; the catcher you pass picks the asset. Pass Referer next to Origin — an SVG pixel embedded
// as <object data="…svg"> sends no Origin, so Referer is the only header naming the page.
val res = phishing.fetchCatcher(
    BotbyePhishingCatcher.Png,
    origin = request.getHeader("Origin"),
    referer = request.getHeader("Referer"),
)

// The SVG names the URL it embeds as the nested pixel: point it at your own PNG endpoint so that fetch
// proxies through your origin too (BotBye honours it only as an absolute http(s) URL). It is a
// constructor parameter, not an optional argument, so an SVG without one does not compile.
val svg = phishing.fetchCatcher(
    BotbyePhishingCatcher.Svg("https://your-site.example/example.png"),
    // Svg(url, skipExecution = false) opts into the script-carrying variant; the default is script-less,
    // no JS on the page.
    origin = request.getHeader("Origin"),
    referer = request.getHeader("Referer"),
)

res.status   // 200
res.headers  // {Content-Type=image/png, ...}
res.body     // ByteArray — raw image bytes to relay back to the browser
res.error    // BotbyeError? — non-null on transport failure
```

`fetchCatcher` is a `suspend` function (like `evaluate`); wrap it in `runBlocking` in a servlet context.

`format`, `image_id` and `executable` are set by the call and never read off the request: the endpoint
you expose is public, so a query on it must not be able to redirect the nested pixel fetch or pick the
SVG variant behind your back. Only `module_name` and `module_version` pass through, and only via the
extractor below. `executable` is always sent, never omitted, so the variant never rides on the backend's
default for a missing param.

Like the evaluate client, the phishing client supports a request extractor so a framework SDK maps a
raw request to a `BotbyePhishingRequestInfo` once and callers pass only their request object. The
extractor is the single place that reads the request — headers and query alike:

```kotlin
import com.botbye.phishing.BotbyePhishingCatcher
import com.botbye.phishing.BotbyePhishingClient
import com.botbye.phishing.BotbyePhishingRequestExtractor
import com.botbye.phishing.BotbyePhishingRequestInfo

val phishing: BotbyePhishingClient<HttpServletRequest> = BotbyePhishingClient.withExtractor(
    BotbyePhishingConfig(clientKey = "<public-client-key>"),
    BotbyePhishingRequestExtractor { req ->
        BotbyePhishingRequestInfo(
            origin = req.getHeader("Origin"),
            referer = req.getHeader("Referer"),
            query = req.parameterMap.mapValues { (_, v) -> v.toList() },
        )
    },
)

val res = phishing.fetchCatcher(request, BotbyePhishingCatcher.Png)
val svg = phishing.fetchCatcher(
    request,
    BotbyePhishingCatcher.Svg("https://your-site.example/example.png"),
)
```

`fetchCatcher` returns `BotbyePhishingResponse`:

| Field | Type | Description |
|---|---|---|
| `status` | `Int` | Upstream HTTP status. A transport failure has none, so it reports the gateway status it means: `504` for a timeout, `502` for anything else |
| `headers` | `Map<String, String>` | Response headers (e.g. `Content-Type`) |
| `body` | `ByteArray` | Raw image bytes (PNG or SVG, per the catcher you passed) |
| `error` | `BotbyeError?` | Normalized transport error: `timeout`, `connection error`, or `invalid json response` |

## Response

`BotbyeEvaluateResponse` contains:

| Field | Type | Description |
|---|---|---|
| `requestId` | `UUID?` | Request UUID |
| `decision` | `BotbyeDecision` | `ALLOW`, `CHALLENGE`, or `BLOCK` |
| `riskScore` | `Double?` | Overall risk score (0–1) |
| `scores` | `Map<String, Double>?` | Per-dimension scores (`bot`, `ato`, `abuse`, ...) |
| `signals` | `Set<String>?` | Triggered signal names (e.g., `BruteForce`, `ImpossibleTravel`) |
| `challenge` | `BotbyeChallenge?` | Challenge type (when decision is `CHALLENGE`) |
| `extraData` | `BotbyeExtraData?` | Enriched device data (IP, country, browser, device, etc.) |
| `error` | `BotbyeError?` | Error details (on fallback) |
| `botbyeResult` | `String?` | Encoded result for Level 1→2 propagation |

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

When using both levels, propagate the Level 1 result to Level 2 via the `botbyeResult` field from the response. This allows the platform to link both evaluations by `requestId` and combine bot score from Level 1 with risk scores from Level 2 into a single unified result:

```kotlin
// Level 1 (proxy) — validate and get result
val l1Response = botbye.evaluate(BotbyeValidationEvent(...))

// Pass botbyeResult to Level 2 (e.g. via header or directly)
val l2Response = botbye.evaluate(BotbyeRiskScoringEvent(
    // ...
    botbyeResult = l1Response.botbyeResult,
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

The SDK follows a **fail-open** strategy. On network or server errors, `evaluate()` returns a default response (`BotbyeDecision.ALLOW` with error details) instead of throwing:

```kotlin
val response = botbye.evaluate(event)

if (response.error != null) {
    // Evaluation failed, request was allowed by default
    logger.warn(response.error.message)
}
```

## Request Extractors (framework integration)

Instead of building events field-by-field at every call site, describe **once** how to turn your
framework's request object into a `BotbyeRequestInfo`, then pass only the raw request to the
`evaluate*` methods. Build the client with `Botbye.withExtractor(...)` — the type parameter is your
framework request type:

```kotlin
import com.botbye.protection.Botbye
import com.botbye.protection.BotbyeConfig
import com.botbye.protection.BotbyeRequestExtractor
import com.botbye.protection.model.BotbyeRequestInfo
import com.botbye.common.http.Headers

val botbye: Botbye<HttpServletRequest> = Botbye.withExtractor(
    config = BotbyeConfig(serverKey = "your-server-key"),
    extractor = BotbyeRequestExtractor { req ->
        BotbyeRequestInfo(
            ip = req.remoteAddr,
            headers = Headers(req.headerNames.toList().associateWith { req.getHeaders(it).toList() }),
            requestMethod = req.method,
            requestUri = req.requestURI,
            token = req.getParameter("botbye_token"),
        )
    },
)
```

Now the call sites only pass the raw request (plus user/event for Level 2):

```kotlin
import com.botbye.protection.model.BotbyeEventStatus
import com.botbye.protection.model.BotbyeUserInfo

// Level 1 — bot validation
val l1 = botbye.evaluateValidation(request)

// Level 2 — risk scoring & event logging
val l2 = botbye.evaluateRiskScoring(
    request = request,
    user = BotbyeUserInfo(accountId = userId),
    eventType = "LOGIN",
    eventStatus = BotbyeEventStatus.SUCCESSFUL,
    botbyeResult = l1.botbyeResult,
)

// Level 1+2 combined (no separate proxy)
val full = botbye.evaluateFull(
    request = request,
    user = BotbyeUserInfo(accountId = userId),
    eventType = "LOGIN",
    eventStatus = BotbyeEventStatus.FAILED,
)
```

For `evaluateValidation` and `evaluateFull`, an explicit `token` argument overrides the one returned
by the extractor (`token ?: extracted.token`). `evaluateRiskScoring` takes no token — Level 2 links to
Level 1 via `botbyeResult`; a token together with user/event context is a combined call, so use
`evaluateFull` instead.

### Spring (HttpServletRequest)

```kotlin
val botbye: Botbye<HttpServletRequest> = Botbye.withExtractor(
    BotbyeConfig(serverKey = "..."),
    BotbyeRequestExtractor { req ->
        BotbyeRequestInfo(
            ip = req.remoteAddr,
            headers = Headers(req.headerNames.toList().associateWith { req.getHeaders(it).toList() }),
            requestMethod = req.method,
            requestUri = req.requestURI,
            token = req.getParameter("botbye_token"),
        )
    },
)

// in a filter / interceptor:
if (botbye.evaluateValidation(request).isBlocked) {
    response.sendError(403, "Access denied")
}
```

### Ktor (ApplicationRequest)

```kotlin
import io.ktor.server.request.*
import com.botbye.common.http.Headers
import com.botbye.protection.BotbyeRequestExtractor

val botbye: Botbye<ApplicationRequest> = Botbye.withExtractor(
    BotbyeConfig(serverKey = "..."),
    BotbyeRequestExtractor { req ->
        BotbyeRequestInfo(
            ip = req.local.remoteAddress,
            headers = Headers(req.headers.toMap()),
            requestMethod = req.httpMethod.value,
            requestUri = req.uri,
            token = req.queryParameters["botbye_token"],
        )
    },
)

// in a plugin / interceptor:
if (botbye.evaluateValidation(call.request).isBlocked) {
    call.respond(HttpStatusCode.Forbidden, "Access denied")
}
```

> The explicit-event API is always available too: `Botbye(config)` returns a `Botbye<Nothing>` on
> which you call `evaluate(BotbyeValidationEvent(...))` etc. with no extractor.

## Custom HTTP Transport

The SDK depends only on the `BotbyeHttpClient` interface; OkHttp is the default
(`OkHttpBotbyeClient`). To run on a different HTTP stack, implement the interface and pass it to the
client constructor / factory:

```kotlin
import com.botbye.common.http.BotbyeHttpClient
import com.botbye.common.http.BotbyeHttpRequest
import com.botbye.common.http.BotbyeHttpResponse

class MyHttpClient : BotbyeHttpClient {
    override val type = "my-client"
    override suspend fun call(request: BotbyeHttpRequest): BotbyeHttpResponse { /* ... */ }
}

val botbye = Botbye(config = BotbyeConfig(serverKey = "..."), client = MyHttpClient())
```

## Lifecycle

Construct the client **once** and reuse it for the lifetime of your application — it owns a connection
pool and a dispatcher thread pool. Both `Botbye` and `BotbyePhishingClient` implement `Closeable`:

```kotlin
botbye.close() // shuts down the default OkHttp transport (dispatcher + connection pool)
```

`close()` only shuts down the transport the SDK created for you. If you passed your own
`BotbyeHttpClient`, the SDK never closes it — you own its lifecycle.

## Helpers

| Helper | Description |
|---|---|
| `createFallbackEvaluationResult(message)` | Build a fail-open `BotbyeEvaluateResponse` (`ALLOW` + `error`) for your own short-circuit paths. |
| `BotbyeErrors` | Normalized error message constants: `SDK_ERROR`, `UNKNOWN_ERROR`, `TIMEOUT_ERROR`, `CONNECTION_ERROR`, `JSON_ERROR`. |

## Testing

```bash
./gradlew build
./gradlew test
```

## License

MIT

## Support

For support, visit [botbye.com](https://botbye.com) or contact [accounts@botbye.com](mailto:accounts@botbye.com).
