# payments-collections-service

A payments microservice for initiating collections, tracking payment lifecycle state, and settling asynchronously through a signed gateway webhook. Spring Boot 4 / Java 21, Maven. It is a genuinely separate deployable service from [core-banking-service](https://github.com/sonofnos/core-banking-service): it never touches that service's database, only its REST API, over HTTP, behind an interface so this repo's own tests don't depend on it being up.

## The idempotency story

A client initiating a collection retries the same request (client timeout, network blip, a mobile app resending on reconnect) with the same `idempotencyKey`. `PaymentService.initiateCollection` looks the key up first; if it exists, it returns the original payment untouched — no second debit.

The part that actually needed proving is what happens when two requests carrying the same key race each other. A unique DB constraint on `payments.idempotency_key` is what enforces it, not an in-memory check (an in-memory "have I seen this key" map doesn't survive a second instance, and doesn't close the race window anyway). Whichever insert wins commits; the loser gets a `DataIntegrityViolationException` from Postgres, which the service catches and turns into "read the winner's row back and return that instead." `IdempotencyConcurrencyIntegrationTest` fires 20 concurrent requests with the same key at a real Postgres (via Testcontainers) and asserts exactly one row exists and every caller got the same payment id back.

## A real bug this repo had

`Payment`'s id is a client-generated UUID assigned in the constructor, not a DB identity column. Spring Data JPA's default "is this a new entity?" heuristic falls back to the `@Version` field when there's no auto-generated id to check — and it treats version `0` as "new" on *every* save call, not just the first. That meant the second save in a single request (INITIATED → PROCESSING) called `persist()` again instead of `merge()`, which Hibernate surfaced as a spurious `ObjectOptimisticLockingFailureException` on a completely single-threaded, non-concurrent request. `CollectionApiIntegrationTest.initiateThenFetchByIdRoundTrips` caught it immediately. Fixed by having `Payment implements Persistable<UUID>` with an explicit transient `isNew` flag that flips to `false` via `@PostPersist`/`@PostLoad`.

Also found in development: Spring Boot 4.1's own auto-configured `ObjectMapper` moved to a new `tools.jackson` (Jackson 3) engine, but springdoc-openapi and jjwt-jackson still pull in classic `com.fasterxml.jackson` (2.x) — nothing wires that classic `ObjectMapper` as a bean anymore, so anything injecting it (webhook body parsing, Redis Streams event serialization) failed to start. Fixed with an explicit `JacksonConfig` bean. And: Boot 4 dropped Flyway auto-configuration outright (no `spring-boot-autoconfigure` support for it), so migrations here run explicitly in `FlywayConfig` against the datasource before Hibernate ever validates the schema against it.

## State machine

`PaymentStatus`: `INITIATED → PROCESSING → SETTLED | FAILED`. `Payment.transitionTo()` is the only way to move state and throws `InvalidStatusTransitionException` on anything not in that graph (no skipping straight to `SETTLED`, no leaving a terminal state). A collection debits the payer via core-banking-service and sits in `PROCESSING` until the gateway's webhook confirms settlement — settlement is genuinely asynchronous, not a same-request optimistic mark. A duplicate webhook delivery for an already-terminal payment is ignored rather than re-applied (no double-credit on webhook retry), and the `@Version` column backstops the same race for two webhook deliveries that both arrive while the payment is still non-terminal: the status transition is committed before the credit call to core-banking-service, so the loser of that race never reaches the money-moving call.

One thing this doesn't solve: there's no distributed transaction across the two services, so a credit failure *after* winning that lock leaves the payment `SETTLED` without the credit leg confirmed. A production version of this would need an outbox + retry (or a saga). Documented here rather than hidden.

## core-banking-service integration

`AccountClient` is the boundary: `HttpAccountClient` (real `RestClient` calls) and `FakeAccountClient` (in-memory, used everywhere in this repo's test suite) both implement it. Base URL is `core-banking.base-url` / `CORE_BANKING_BASE_URL`. core-banking-service wasn't deployed yet at the time this was built, so this points at a documented contract:

```
GET  /api/accounts/{id}/balance   -> { accountId, amount, currency }
POST /api/accounts/{id}/debit     { amount, currency, reference } -> { accountId, newBalance, currency, reference }
POST /api/accounts/{id}/credit    same shape
```

core-banking-service is now live at https://sonofnos-core-banking.onrender.com (Render free tier, sleeps after inactivity) — `CORE_BANKING_BASE_URL` is set to that in the Render deployment of this service below.

## Redis: balance caching + event bus

- **Balance cache**: `AccountBalanceService` caches `GET .../balance` lookups for 30s (short TTL — cheap to re-fetch, but a stale balance after a debit is a correctness bug, not just an inconvenience) and evicts the cache for any account this service just moved money through.
- **Event bus**: payment lifecycle events (`INITIATED`/`PROCESSING`/`SETTLED`/`FAILED`) are published through `EventPublisher`, a Kafka-shaped interface (`publish(topic, event)`). The only implementation, `RedisStreamsEventPublisher`, backs it with a Redis Stream (`XADD`), not Kafka. **Real Kafka was not wired up** — a free managed broker (Upstash Kafka, Confluent Cloud) couldn't be provisioned non-interactively within the time budgeted for it, mostly because every option gates the actual broker credentials behind an interactive web signup. Swapping in a real Kafka producer later is a new implementation of `EventPublisher` plus a config change, not a redesign of anything that calls it.

## Auth

Self-issued JWT, same approach as core-banking-service: this service is its own token issuer (`POST /auth/token`, demo/testing only — no external IdP dependency) and its own resource server (HMAC-SHA256, `NimbusJwtDecoder.withSecretKey`, shared secret via `app.jwt.secret`). Roles claim (`PAYMENTS_WRITE`, `PAYMENTS_READ`) maps to Spring Security `ROLE_*` authorities and gates the collection endpoints with `@PreAuthorize`. The webhook endpoint is deliberately outside this filter chain — a payment gateway isn't an OAuth2 client of ours; HMAC is the auth there, exactly like a real gateway webhook.

## Webhook signature verification

`WebhookSignatureService` computes HMAC-SHA256 over the raw request body with a shared secret (`webhook.hmac-secret`) and compares in constant time (`MessageDigest.isEqual`) to avoid a timing side-channel. `WebhookController` reads the body as a raw string specifically so the bytes being verified are exactly the bytes that were signed — parsing to a DTO first and re-serializing to verify would let field-order/whitespace differences silently break real signatures.

## Running it

```bash
docker compose up -d --wait   # Postgres :5437, Redis :6379
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw spring-boot:run
```

Swagger UI: http://localhost:8081/swagger-ui.html
Health: http://localhost:8081/actuator/health

Postman collection: `postman/payments-collections-service.postman_collection.json` (issue a token, initiate a collection, replay it, check status, sign and send a webhook).

Example webhook signature from the shell:
```bash
BODY='{"paymentId":"<uuid>","success":true}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_HMAC_SECRET" | sed 's/^.* //')
curl -X POST localhost:8081/api/webhooks/gateway-settlement \
  -H "Content-Type: application/json" -H "X-Webhook-Signature: $SIG" -d "$BODY"
```

## Tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw test
```

- JUnit 5 + Mockito unit tests: `PaymentStatusTest` (every transition in the state graph, valid and invalid), `PaymentServiceTest` (idempotent replay, debit failure, webhook settlement, duplicate-webhook-ignored — all against `FakeAccountClient`, no real HTTP), `WebhookSignatureServiceTest` (valid, tampered, wrong secret, blank).
- Testcontainers integration tests (real Postgres + Redis, Docker required): `IdempotencyConcurrencyIntegrationTest` (the 20-thread race described above), `AccountBalanceCachingIntegrationTest` (cache hit/evict against real Redis), `CollectionApiIntegrationTest` (full HTTP stack: JWT auth, initiate/replay/fetch, signed and mis-signed webhooks).

## Spring Boot version note

This was built in September 2026; Spring Boot 3.x is no longer offered by start.spring.io, so this runs on Spring Boot 4.1.1 (Jakarta namespace, same shape as 3.x) rather than the 3.3+ this was originally scoped for. A few Boot 4 breaking changes surfaced and are documented above and in code comments (`FlywayConfig`, `JacksonConfig`).

## Deployment

- GitHub: https://github.com/sonofnos/payments-collections-service
- CI (GitHub Actions, `.github/workflows/ci.yml`): build + full test suite (including Testcontainers, which run fine on GitHub-hosted runners since Docker is preinstalled), a Trivy vulnerability scan of the built image, and a SonarCloud step gated on a `SONAR_TOKEN` secret that requires a one-time manual sonarcloud.io signup (Sign in with GitHub → Analyze new project → generate token) — left as a clear no-op until that secret is added, not faked. Both jobs are green on `main`.
- Render: web service `sonofnos-payments-collections` and a free Key Value (Redis) instance `payments-redis` are both provisioned and correctly wired (`CORE_BANKING_BASE_URL` points at the live core-banking-service, `REDIS_HOST`/`REDIS_PORT` at the real Redis instance). **The Postgres database is not provisioned**: Render allows exactly one free-tier Postgres per account, and that slot was already held by an unrelated project (`agent-platform`) running concurrently in the same account at deploy time. Rather than spend on a paid plan without asking, or repurpose someone else's live database, this is left as an honest known gap - the Docker image builds and boots correctly on Render (confirmed via deploy logs: it gets as far as Flyway trying to reach Postgres and fails cleanly with a connection-refused, not a crash in application code), and the full stack including Postgres is proven end-to-end via `docker-compose` locally and Testcontainers in CI. Pointing `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` at a real instance once the account's free slot is available (or a low-cost plan is approved) is the only step left.
- Render's free Postgres plan, once provisioned, **expires 30 days after creation** and needs manual renewal — noted here plainly for when that happens.

## Layout

```
domain/            Payment entity + PaymentStatus state machine
repository/         PaymentRepository (unique idempotency_key constraint)
client/              AccountClient interface, HttpAccountClient, FakeAccountClient (test)
service/             PaymentService, AccountBalanceService (Redis cache), WebhookSignatureService
service/event/       EventPublisher interface, RedisStreamsEventPublisher, PaymentEvent
security/            self-issued JWT issuance + resource-server config
web/                 PaymentController, WebhookController, AuthController, DTOs
config/              RestClient/AccountClient wiring, Redis cache, Flyway, Jackson, OpenAPI
```
