# Implementation & Deployment Guide

## Phase 1 — Apply immediately (zero risk, no DB changes)

### malawi-weather

| File | Action |
|---|---|
| `config/CacheConfig.java` | Replace — fixes B13, adds district + apiKey caches |
| `service/ApiKeyService.java` | Replace — adds key caching (P1), async log (P6), IP extract |
| `service/OpenMeteoService.java` | Replace — separate TTLs, timeout (S7), UPSERT cache (B5) |
| `service/DeveloperService.java` | Replace — removes isActive/status desync risk (B4) |
| `security/ApiKeyFilter.java` | Replace — uses cache; cleaner public-path check |
| `exception/GlobalExceptionHandler.java` | **New file** — add to project |
| `repository/ApiKeyRepository.java` | Replace — adds `deactivateAllForDeveloper`, `findFirst...` |
| `repository/DeveloperRepository.java` | Replace — adds `findAllWithLatestKey` |
| `repository/WeatherCacheRepository.java` | Replace — adds `deleteExpired` |
| `repository/DistrictRepository.java` | Replace — adds `@Cacheable` |
| `config/DistrictSeeder.java` | Replace — idempotent per-district seeding (S6) |
| `dto/DeveloperRegistrationRequest.java` | Replace — adds `@Size` guards (B7) |

> **Enable `@Async`**: Add `@EnableAsync` to `WeatherApiApplication.java`.

### mvula-web

| File | Action |
|---|---|
| `lib/http.ts` | **New file** — single source of truth for API_URL + adminAuthHeader |
| `lib/validation.ts` | **New file** — shared email + password validators |
| `lib/admin-auth.ts` | Replace — timing-safe credential check (B9) |
| `app/api/auth/register/route.ts` | Replace — imports from lib/http + lib/validation |
| `app/api/auth/verify/route.ts` | Replace — imports from lib/http |
| `app/api/auth/login/route.ts` | Replace — imports from lib/http + lib/validation |
| `app/api/auth/login/verify/route.ts` | Replace — imports from lib/http |
| `app/api/auth/resend/route.ts` | Replace — imports from lib/validation |
| `app/api/admin/auth/login/route.ts` | Replace — timing-safe (B9) |
| `app/api/admin/stats/route.ts` | Replace — single combined endpoint (D7/P4) |
| `components/ui/OtpInput.tsx` | **New file** — shared OTP input (D4) |
| `components/ui/Banner.tsx` | **New file** — shared error/success banner (D5) |

> **Update `developers/page.tsx`**: Replace the two inline OTP UIs with `<OtpInput>`,
> and replace the six error `<div>` blocks with `<ErrorBanner>` / `<SuccessBanner>`.
>
> **Update admin console**: Change the three `fetchAll()` calls to a single
> `fetch("/api/admin/stats")` that returns all three payloads.

---

## Phase 2 — DB migrations (requires maintenance window or zero-downtime strategy)

Run `MIGRATIONS.sql` in order via your migration tool:

1. **V1** — `UNIQUE (district_name, cache_type)` on `weather_cache`
   - Safe to run online — only blocks concurrent writes to the same row
   - Will fail if duplicate rows already exist; clean first:
     ```sql
     DELETE FROM weather_cache a USING weather_cache b
     WHERE a.id > b.id
       AND a.district_name = b.district_name
       AND a.cache_type = b.cache_type;
     ```

2. **V2** — Indexes on `usage_logs`
   - Use `CREATE INDEX CONCURRENTLY` — no table lock, safe in production

3. **V3** — Backfill `is_active` to match `status`
   - Safe, idempotent UPDATE

4. **V4** — Retention policy for `usage_logs`
   - Schedule via pg_cron or your CI/CD pipeline

5. **V5** — Partial index on `developers.status`
   - `CREATE INDEX CONCURRENTLY` — safe in production

---

## Phase 3 — Future improvements (prioritised backlog)

### High value
- **Redis OTP store** — Replace `global.__mvula_otp_store__` with Redis or Upstash.
  The in-memory store breaks the moment you add a second Vercel instance or
  enable Edge Runtime. All OTP functions already accept an async interface — swap
  the Map for `ioredis` or `@upstash/redis` with no changes to callers.

- **Rate-limit counter table** — Replace the `COUNT(usage_logs)` daily limit check
  with an atomic increment on a dedicated `rate_limit_counters(api_key_id, date, count)`
  table. One `UPDATE ... RETURNING count` per request vs a full count scan.

- **Remove `Developer.isActive`** — Once V3 migration is confirmed and all pods are
  on new code, drop the column. The `@Transient` `isActive()` method derived from
  `status` replaces it.

### Medium value
- **Move legacy desktop app** — The `src/` Swing application and root `pom.xml` belong
  in a separate repo (`malawi-weather-desktop`) or at minimum a `desktop/` subdirectory.
  Their presence at the repo root causes `mvn package` from the root to target the
  wrong artifact.

- **Add MDC correlation IDs** — One line in `ApiKeyFilter`:
  `MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));`
  makes logs traceable end-to-end across controller → service → HTTP calls.

- **Replace `WebClient.block()` with `RestClient`** — Spring 6.1's `RestClient` is the
  idiomatic blocking HTTP client. `WebClient` + `.block()` works but carries the
  reactor-netty dependency unnecessarily in a plain MVC app.

### Lower priority
- **Write tests** — Start with:
  1. `ApiKeyFilterTest` — validates key lookup, daily limit enforcement
  2. `OpenMeteoServiceTest` — Caffeine hit, DB fallback, stale serve on 429
  3. `DeveloperServiceTest` — registration duplicate email, approve idempotency
  4. `OtpInputTest` (Vitest) — paste, backspace, autoFocus behaviour
