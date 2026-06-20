# Malawi Weather API — Full Architecture Audit
### Repos: `Pelex04/malawi-weather` · `Pelex04/mvula-web`
### Auditor: Senior Engineer Review (30+ yrs)

---

## 1. ARCHITECTURE OVERVIEW (Reverse-Engineered)

```
Browser (mvula-web · Next.js 16 · Vercel)
│
├── Public pages: /, /docs, /developers  (client-rendered)
│
├── /app/api/auth/*          → Next.js Route Handlers (server-side)
│   ├── register             → calls Spring backend + sends OTP email
│   ├── verify               → verifies OTP  → calls Spring /admin/api/.../approve
│   ├── login                → validates email → sends login OTP
│   ├── login/verify         → verifies login OTP → returns cached API key
│   └── resend               → refreshes OTP in memory store
│
└── /app/api/admin/*         → Admin proxy (session-cookie-guarded)
    ├── auth/login|logout    → issues/invalidates HMAC-signed httpOnly cookie
    ├── developers           → proxies to Spring /admin/api/*
    └── stats                → proxies to Spring /admin/api/stats
         │
         ▼  HTTP Basic Auth (server-to-server only)
Spring Boot API (Render · Java 21)
│
├── ApiKeyFilter             → validates X-API-Key header, checks daily limit
├── SecurityConfig           → HTTP Basic for /admin/** ; permitAll elsewhere
│
├── /api/v1/districts/**     → Public
├── /api/v1/weather/:d       → ApiKey-gated → OpenMeteoService → L1 Caffeine → L2 DB cache
├── /api/v1/forecast/:d      → ApiKey-gated → OpenMeteoService → L1 Caffeine → L2 DB cache
├── /api/v1/developers/register → Public
└── /admin/api/**            → HTTP Basic gated; developer management + stats
│
└── PostgreSQL (Neon)
    ├── districts (28 rows, seeded once)
    ├── developers           → name, email, status (enum), isActive
    ├── api_keys             → keyValue, dailyLimit, totalRequests
    ├── usage_logs           → per-request log with IP, endpoint, district
    └── weather_cache        → L2 persistent cache (districtName+type → JSON blob)

Legacy desktop app (src/main/java/org/example/) — Swing GUI, separate from API
```

**Data flow — weather request:**
1. Developer hits `GET /api/v1/weather/Lilongwe` with `X-API-Key: mww_xxx`
2. `ApiKeyFilter` → DB lookup for key → checks `isActive`, developer `isActive`, daily limit
3. `WeatherController` → `DistrictRepository.findByNameIgnoreCase()`
4. `OpenMeteoService.getCurrentWeather()`:
   - L1: Caffeine in-memory hit → return immediately
   - L2: PostgreSQL `weather_cache` hit (not expired) → parse + return
   - L3: HTTP → `api.open-meteo.com` → save to DB cache → return
5. `ApiKeyService.logUsage()` → INSERT into `usage_logs`, UPDATE `api_keys` stats

---

## 2. BAD ARCHITECTURE DECISIONS

### B1 — `adminAuthHeader()` duplicated in 4 places
`app/api/auth/verify/route.ts`, `app/api/auth/login/route.ts`, `app/api/auth/login/verify/route.ts`
all manually build `"Basic " + btoa(user+":"+pass)` **inline**. `admin-proxy.ts` has the canonical
version. The other three files never import it — they copy-paste it. One env-var rename breaks three
hidden locations.

### B2 — `API_URL` constant re-declared in every auth route
`const API_URL = process.env.API_URL || "http://localhost:8080"` appears in 4 separate files
instead of being imported from `admin-proxy.ts` which already exports it.

### B3 — In-memory OTP store is a single-instance time bomb
`lib/otp.ts` uses `global.__mvula_otp_store__` (a Node.js `Map`). This works on a single
Vercel/Node instance but silently breaks if you add a second instance (e.g. Vercel Edge, multi-pod
Docker). An OTP issued on pod A cannot be verified on pod B. The code comments mention Redis
as the fix, but nothing enforces it — this is a latent correctness bug.

### B4 — `Developer.isActive` + `Developer.status` are redundant and can desync
The `Developer` entity has both a boolean `isActive` and an enum `Status`. They must always be
kept in sync manually (see comments in code). Wherever one is set, the other must also be set.
This is fragile — the `isActive` boolean should be derived from `Status`, not stored separately.

### B5 — `WeatherCache` has no unique constraint → duplicate rows possible
`WeatherCacheRepository.save()` is called after every fresh fetch. If two concurrent requests
for the same district land simultaneously (before either populates L1 Caffeine), both will miss the
L2 DB cache and both will call Open-Meteo and then both will INSERT a new cache row, creating
duplicates. The query `findByDistrictNameIgnoreCaseAndCacheType` will then return an arbitrary one.

### B6 — `ApiKeyFilter` hits the database on every single request
`apiKeyService.validateKey(keyValue)` does a DB SELECT on every authenticated request.
With 28 districts × many developers, this becomes the hot path bottleneck. The key should be
cached in L1 (Caffeine) too, just like weather data.

### B7 — `DeveloperRegistrationRequest` only has `@Email` + `@NotBlank` — no length limits
There are no `@Size` constraints on `name`, `appName`, `appDescription`. A malicious actor can
send 1 MB strings and cause DB bloat or OOM in the JPA layer.

### B8 — `UsageLog` grows unboundedly
There is no retention policy. Every single API request writes a row. At 1,000 requests/day × 100
developers this is 100,000 rows/day with no archival or deletion. The daily-count query
(`CAST(timestamp AS date) = CURRENT_DATE`) will degrade as the table grows — no partial index
on `timestamp`.

### B9 — Admin login in `mvula-web` compares passwords with `===` (not timing-safe)
```ts
const userMatch = username === ADMIN_USER;
const passMatch = password === ADMIN_PASS;
```
JavaScript string `===` is NOT timing-safe. A timing attack can enumerate the correct characters
one by one. Should use `timingSafeEqual` from the `crypto` module (already imported in
`admin-auth.ts`).

### B10 — `localStorage` stores the raw API key in plain text
`localStorage.setItem("mvula_session", JSON.stringify(s))` where `s.apiKey = "mww_xxx"`.
The key lives in browser storage accessible to any JS on the page (XSS risk). Rotating the key
(invalidating a compromised key) also requires the developer to log in again — there's no
self-service rotation endpoint.

### B11 — Legacy desktop app (`src/`) lives in the same repo as the production API (`api/`)
The root `pom.xml` and `src/` are an old Swing desktop prototype. They share no code with
`api/` but sit at the repo root, causing confusion about what is the "real" project. The root
`pom.xml` points to `org.example` (the desktop app), not the API.

### B12 — `OpenMeteoService` blocks the reactive thread
`webClient.get()...bodyToMono(String.class).block()` is called inside a Spring MVC (Tomcat)
thread pool. `block()` on WebFlux is valid in MVC but wastes a thread during the entire I/O wait.
Using `RestClient` (Spring 6.1) is cleaner and semantically correct for blocking contexts.

### B13 — `CacheConfig` uses one TTL (30 min) for both `currentWeather` and `forecast` caches
`OpenMeteoService` defines `FORECAST_TTL = 180` minutes in code, but `CacheConfig` sets
**both** Caffeine caches to 30 minutes. The L1 Caffeine forecast cache evicts 6× too fast,
causing unnecessary L2/L3 fetches for forecast data.

---

## 3. DUPLICATE LOGIC

| # | What is duplicated | Where |
|---|---|---|
| D1 | `adminAuthHeader()` | `admin-proxy.ts`, `verify/route.ts`, `login/route.ts`, `login/verify/route.ts` |
| D2 | `const API_URL = process.env.API_URL \|\| "..."` | Same 4 files |
| D3 | `getWindDirection(deg)` | `OpenMeteoService.java` (server) AND `lib/utils.ts` (client). The server computes it and includes it in the response; the client re-implements it unnecessarily |
| D4 | OTP digit-input + keyboard handler | `VerifyForm` and `LoginForm` in `developers/page.tsx` — identical `handleDigit`, `handleKeyDown`, `refs`, `digits` state. Should be one `<OtpInput>` component |
| D5 | Error banner JSX | `<div className="p-4 bg-red-50 ...">` repeated 6+ times across `developers/page.tsx` |
| D6 | Email regex | Duplicated in `register/route.ts` and `login/route.ts`; a shared validator util would be cleaner |
| D7 | `fetchAll()` in admin console calls 3 APIs that could be combined into one | `stats`, `developers`, `pending` — 3 round-trips every 30s instead of 1 |

---

## 4. PERFORMANCE BOTTLENECKS

| # | Issue | Impact |
|---|---|---|
| P1 | DB hit on every API request for key validation (ApiKeyFilter) | Adds 5–20ms to every request; scales linearly with traffic |
| P2 | `usageLogRepository.countTodayUsage(keyId)` on every request — no index on `(api_key_id, timestamp::date)` | Full sequential scan of usage_logs for active keys |
| P3 | `WeatherController` calls `districtRepository.findByNameIgnoreCase()` on every weather request — 28-row table hit | Unnecessary DB round-trip; districts never change after seeding |
| P4 | Admin console polls 3 separate API routes every 30s — 3 HTTP requests from browser | Should be one endpoint returning all admin state |
| P5 | `DeveloperService.revokeDeveloper()` loads `developer.getApiKeys()` — EAGER/LAZY risk | If `apiKeys` is LAZY (it is), this triggers N+1 if there are many keys per developer |
| P6 | `UsageLog` INSERT on every request — synchronous, blocks response | Async write queue would eliminate this from the hot path |

---

## 5. SCALABILITY RISKS & MAINTENANCE ISSUES

| # | Risk |
|---|---|
| S1 | In-memory OTP store (global Map) — breaks immediately on second instance |
| S2 | Caffeine L1 cache is per-instance — no shared cache across multiple API pods |
| S3 | `weather_cache` (PostgreSQL) stores raw JSON blobs — no schema versioning; a response format change from Open-Meteo would silently serve stale malformed JSON from cache |
| S4 | `dailyLimit` is enforced by counting `usage_logs` rows — this becomes the bottleneck table; no rate-limit counter table optimised for increment |
| S5 | Admin password stored in environment variable, compared as plaintext string — no rotation mechanism |
| S6 | `DistrictSeeder` uses `districtRepository.count() > 0` guard — if a partial seed occurred (e.g. only 10 rows before a crash), it would never re-seed the missing 18 |
| S7 | No request timeout on `WebClient` calls to Open-Meteo — a slow upstream response holds a Tomcat thread indefinitely |
| S8 | `WeatherCache` has no UNIQUE constraint at DB level — only application logic prevents duplicates |
| S9 | No structured logging (MDC correlation IDs) — impossible to trace a single request through controller → service → cache → HTTP call |
| S10 | No tests — zero test files in either repo |

---

## 6. CLEAN ARCHITECTURE BREAKDOWN

### malawi-weather (Spring Boot API)

```
api/src/main/java/mw/pelex/weatherapi/
│
├── config/
│   ├── CacheConfig.java          — separate TTLs for currentWeather vs forecast
│   ├── CorsConfig.java           — centralised CORS (correct)
│   ├── DistrictSeeder.java       — idempotent seed guard (needs count-per-region fix)
│   └── SecurityConfig.java       — HTTP Basic for /admin/**; ApiKeyFilter elsewhere
│
├── controller/
│   ├── WeatherController.java    — thin; delegates to service; no business logic
│   ├── DeveloperController.java  — registration only (correct)
│   └── AdminController.java      — management + stats
│
├── dto/                          — clean; no entity leakage
│
├── exception/                    ← MISSING — add GlobalExceptionHandler
│
├── model/                        — Domain entities; isActive redundancy needs removal
│
├── repository/                   — JPA; missing indexes on usage_logs
│
├── security/
│   └── ApiKeyFilter.java         — needs L1 key cache
│
└── service/
    ├── ApiKeyService.java        — logUsage() should be async
    ├── DeveloperService.java     — correct; idempotent approve (good)
    └── OpenMeteoService.java     — needs separate TTL config; UPSERT for cache
```

### mvula-web (Next.js)

```
mvula-web/
│
├── lib/
│   ├── admin-auth.ts     — HMAC session (correct)
│   ├── admin-proxy.ts    — canonical adminAuthHeader() + API_URL (good)
│   ├── api.ts            — public API client (correct)
│   ├── email.ts          — nodemailer (correct)
│   ├── otp.ts            — in-memory store (needs Redis)
│   ├── utils.ts          — duplicates getWindDirection; needs email validator
│   └── validation.ts     ← MISSING — centralise email regex, sanitisers
│
├── components/
│   ├── layout/Nav.tsx    — correct
│   └── ui/               ← MISSING — OtpInput, ErrorBanner shared components
│
└── app/api/
    ├── auth/*            — stop re-declaring API_URL + adminAuthHeader
    └── admin/*           — correct proxy pattern
```

---

## 7. REFACTORING STRATEGY (Priority Order)

### 🔴 Critical (fix before production load)

1. **De-duplicate `adminAuthHeader()` + `API_URL`** — import from `admin-proxy.ts` (5 min fix, prevents credential drift)
2. **Add UNIQUE constraint on `weather_cache(district_name, cache_type)`** + use UPSERT — prevents duplicate cache rows under concurrent load
3. **Cache API key lookups in Caffeine** — eliminates DB hit on every request
4. **Add `@Index` on `usage_logs(api_key_id, timestamp)`** — prevents table scan for rate-limit check
5. **Timing-safe password comparison in admin login** — use `crypto.timingSafeEqual`
6. **Add `WebClient` timeout** — prevent thread starvation on slow Open-Meteo

### 🟡 High (fix in next sprint)

7. **Fix Caffeine TTL for forecast cache** — set 180 min, not 30 min
8. **Cache `districts` in Caffeine** — 28-row immutable table; no reason to hit DB per request
9. **Add `@Size` constraints** on registration DTOs — prevent oversized inputs
10. **Make `usageLog` write async** — `@Async` + `ThreadPoolTaskExecutor`, removes from hot path
11. **Extract `OtpInput` component** in Next.js — remove ~80 lines of duplicated OTP UI
12. **Extract `ErrorBanner` component** — remove repeated JSX

### 🟢 Maintainability (next quarter)

13. **Remove `Developer.isActive`** — derive from `status` enum; add `@Transient` computed property
14. **Add `@PartialIndex` on `usage_logs` for today's rows** or migrate to a `rate_limit_counters` table
15. **Add `GlobalExceptionHandler`** (`@ControllerAdvice`) — remove try/catch boilerplate from controllers
16. **Replace `WebClient` with `RestClient`** (Spring 6.1) — cleaner blocking HTTP
17. **Move legacy desktop app** (`src/`) to its own repo or `desktop/` subdirectory
18. **Add integration tests** — at minimum: key validation, daily limit, cache fallback

---

## 8. PRODUCTION-GRADE REFACTORED CODE

See attached files. Changes made:
- Zero functionality changes
- All fixes are purely structural, safety, and performance improvements
