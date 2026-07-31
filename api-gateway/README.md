# api-gateway

## Responsibility

`api-gateway` is the single entry point for every client request into PayForge. It
centralizes request routing — and, later, authentication and rate limiting — so
individual business services never have to duplicate that logic themselves.

It does **not**:
- contain any business logic of its own (account/ledger/notification concerns all live
  in their own services)
- talk to any database directly
- perform service discovery via a registry (no Eureka, no discovery-client dependency) —
  routes resolve services by plain DNS name instead: `localhost` in local development,
  Docker Compose's built-in DNS once services are containerized, and Kubernetes Service
  DNS in the cluster. See `docs/service-discovery-decision.md` for the full reasoning.

---

## Routing

| Route ID | Predicate | Forwards to |
|---|---|---|
| `account-route` | `Path=/api/v1/accounts/**` | `account-service` |
| `ledger-route` | `Path=/api/v1/ledger/**,/api/v1/payment-intents/**` | `ledger-service` |

**Multiple path patterns on one route use a single comma-separated `Path` predicate,
not multiple separate `Path` entries.** Two separate predicate entries on the same route
are ANDed together, not ORed — meaning a request would have to match *both* patterns
simultaneously to route at all, which is never possible for a single request. This was
caught and fixed during setup (`ledger-route` originally had two separate `Path` entries
and would never have matched any real request).

**Predicate factory names are case-sensitive.** It's `Path`, not `path` — a lowercase
predicate name fails to resolve to Spring Cloud Gateway's `PathRoutePredicateFactory`
entirely, so the route silently never matches. Also caught and fixed during setup.

---

## Known Spring Cloud 2025.0.3 issues affecting this module

- `spring-cloud-starter-gateway` is deprecated for this version line — use
  `spring-cloud-starter-gateway-server-webflux`, with config nested under
  `spring.cloud.gateway.server.webflux.*` (not the older `spring.cloud.gateway.*` path).
- **Filter shorthand string syntax is broken** (upstream issue #4039) —
  `- AddResponseHeader=X,Y` throws `Unable to find GatewayFilterFactory` even though the
  factory bean is registered correctly. Predicates (`Path=...`) are unaffected — only
  filters. **This project always uses long-form `name:`/`args:` YAML for every filter**,
  as seen in `default-filters` below. Do not switch back to shorthand syntax.
- `spring-cloud-dependencies` BOM must be explicitly imported in the parent POM, or
  Spring Cloud dependency versions resolve as "unknown."

---

## Default filters

```yaml
default-filters:
  - name: AddResponseHeader
    args:
      name: X-Gateway
      value: payforge-api-gateway
```

Applied to every route. Useful for confirming, from any client, that a response actually
came back through the Gateway (check for the `X-Gateway` header) rather than hitting a
service directly by accident during testing.

---

## Configuration profiles — local vs. containerized

The base `application.yml` is written for the **eventual containerized state** — routes
point at plain service DNS names (`http://account-service:8081`,
`http://ledger-service:8082`), which only resolve once these services run inside the
same Docker Compose network or Kubernetes cluster.

**Locally, before services are containerized, DNS names like `account-service` don't
resolve on your machine at all.** `application-local.yml` overrides just the route
`uri` values to `localhost`, keeping route `id`s and predicates identical to the base
file — only the target address differs.

Run locally with the `local` profile active:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run
```

Confirm `The following 1 profile is active: "local"` appears in the startup log — this
confirms the override actually merged, not just that the app started.

**Windows note:** the command-line argument form
(`mvn spring-boot:run "-Dspring-boot.run.profiles=local"`) has been unreliable on this
setup — the environment-variable form above (`$env:SPRING_PROFILES_ACTIVE`) is the
reliable one to use.

---

## Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: gateway, health
  endpoint:
    gateway:
      access: read-only
```

`gateway.access: read-only` mitigates **CVE-2025-41243** — an unsecured actuator gateway
endpoint would otherwise allow route configuration to be inspected or modified over
HTTP. `read-only` permits inspection (useful for debugging route matching) without
allowing runtime route mutation.

---

## Running locally, end to end

1. Start Postgres and any dependent databases (from repo root):
   ```bash
   docker compose up -d
   ```
2. Start `account-service` (port `8081`), then `ledger-service` (port `8082`, once it
   exists) in their own terminals.
3. Start the gateway with the local profile active (see above). Runs on port `8080`.
4. Verify routing works — request the Gateway's port, not a service's port directly:
   ```bash
   curl http://localhost:8080/api/v1/accounts/<some-account-id>
   ```
   A `200` with the account JSON, and an `X-Gateway: payforge-api-gateway` response
   header, confirms the request was actually routed through the Gateway to
   `account-service`, not hit directly.

---

## Deferred to later stages of the build (deliberate, not missing)

| Concern | Where it actually belongs |
|---|---|
| JWT validation at the Gateway | Security/auth phase — centralizing auth here so downstream services don't each reimplement it |
| Rate limiting (`RequestRateLimiter`) | Same phase as JWT — affected by the same filter-shorthand bug noted above; long-form YAML required |
| Circuit breaker / Resilience4j on downstream calls | Resilience phase, closing the gap where a downstream service being down currently surfaces as a raw `500` instead of a clean `503` |
| notification-service route | Added once that service is scaffolded |