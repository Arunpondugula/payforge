# ledger-service

## Responsibility

`ledger-service` owns **transactions and the double-entry ledger** for PayForge. It is
the single source of truth for "what money movements actually happened, and do they
balance."

It does **not**:
- own account identity or the recorded balance figure itself (that's `account-service`'s
  responsibility — `ledger-service` only *asks* `account-service` about balances via its
  Feign client, it never stores or computes a balance of its own)
- touch card/payment data directly yet (PCI scope narrowing to this service specifically
  is a Sprint 3 decision, not made yet — flagged here so it isn't assumed prematurely)
- handle authentication/login (arrives with the security/auth phase, centralized at the
  Gateway, same as every other service)

This mirrors how Stripe separates its internal ledger (transactions, balance
transactions, double-entry postings) from the `Customer`/`Account` object that merely
displays a computed balance — the ledger is the record of *movements*; the balance is a
number derived from summing them.

---

## Database

Own Postgres schema, `ledgerdb`, isolated from every other service's database —
enforced at the infrastructure level (separate DB, separate credentials), not just by
convention. See `docker-compose.yml` at the repo root for the `ledger-db` container
definition.

| Setting | Value |
|---|---|
| Host (local dev) | `localhost:5433` |
| Database | `ledgerdb` |
| User | `payforgeledger` |
| Schema management | Hibernate `ddl-auto: update` (local dev only — same caveat as `account-service`) |

**Port note:** `ledger-db` maps to host port `5433`, not the default `5432`, because
`account-db` already claims `5432` on the host. Inside the shared Docker network, both
containers still listen on their own internal `5432` — the `5433` mapping only matters
for connections originating from *outside* Docker (e.g. `psql` run directly on the host).

**Local-dev-only shortcut, flagged honestly (same caveat as `account-service`):**
`ddl-auto: update` auto-creates/alters tables from entity classes, but will never drop a
column removed from an entity. A real production system uses versioned migrations
(Flyway/Liquibase) instead.

---

## Entities

### `Transaction`

The parent record — one row per payment event.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key, client-generated (`GenerationType.UUID`), same rationale as `Account.id` — no round trip, no cross-service ID collisions. |
| `status` | `TransactionStatus` enum (`PENDING`, `COMPLETED`, `FAILED`) | Stored via `@Enumerated(EnumType.STRING)`, never ordinal — same reasoning as `Account.status`. This is deliberately the *minimal* state set; refund/dispute states (`REFUNDED`, `DISPUTED`) arrive with Sprint 2's state-machine work, not before. |
| `idempotencyKey` | `String`, unique | Column exists now so the schema doesn't need a later migration. The actual enforcement logic (checking this *before* doing any real work) is Day 10's task — right now the column is present but nothing reads it yet. |
| `entries` | `List<LedgerEntry>` | **Not a real database column.** A computed, JPA-managed mirror of whichever `LedgerEntry` rows have this transaction's id as their foreign key. See the `LedgerEntry` section below for where the real column lives. |
| `createdAt` | `Instant`, immutable (`updatable=false`) | Set once, via `@PrePersist`, never touched again — same tamper-resistance reasoning as `Account.createdAt`. |

**No public setters** on `status` — only `markCompleted()` / `markFailed()`, enforcing
that a transaction's lifecycle only ever moves through deliberate, named transitions,
never an arbitrary field assignment.

### `LedgerEntry`

The child record — one row per individual debit or credit within a transaction. A
`Transaction` always has **two or more** `LedgerEntry` rows.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key, client-generated, same pattern as everywhere else in PayForge. |
| `transaction` | `Transaction` (`@ManyToOne`) | **The real foreign key.** Backed by an actual `transaction_id` column. This is the owning side of the relationship — `Transaction.entries` is mapped *from* this field (`mappedBy = "transaction"`), not the other way around. |
| `accountId` | `UUID` | **Deliberately a plain field, not a JPA relationship.** `Account` lives in a completely separate database (`accountdb`), owned by `account-service`. No foreign key across service boundaries is possible or correct here — the database engine itself has no mechanism to validate a row against a table in a different database instance. The only way to confirm an `accountId` is real is to ask `account-service`, over HTTP, via the Feign client. |
| `entryType` | `EntryType` enum (`DEBIT`, `CREDIT`) | `@Enumerated(EnumType.STRING)`, same ordinal-safety reasoning as every other enum in this project. |
| `amount` | `BigDecimal`, `NUMERIC(19,4)` | Same precision discipline as `Account.balance` — never `float`/`double`. |
| `createdAt` | `Instant`, immutable | Same `@PrePersist` pattern as `Transaction.createdAt`. |

**Relationship mechanics, stated precisely:** `cascade = CascadeType.ALL` on
`Transaction.entries` means deleting or saving a whole `Transaction` automatically
cascades to every one of its `LedgerEntry` rows — correct here because a `LedgerEntry`
has no independent meaning outside the `Transaction` it belongs to.
`orphanRemoval = true` covers the narrower, separate case of removing a single entry
from that list while the `Transaction` itself still exists — without it, that row would
become an invisible-but-still-present orphan in the database, a genuinely dangerous
state for financial data.

**How two entries end up sharing one `transaction_id`:** `Transaction.addEntry()` sets
`entry.setTransaction(this)` for every entry added — meaning every entry added to the
same in-memory `Transaction` object holds a reference to that *exact same object*, not
a copy. When the whole aggregate is saved, Hibernate reads each entry's
`transaction.getId()` to populate its foreign key — since every entry points at the
same object, they all get the same id written.

**One-sided movements (e.g. a withdrawal) still produce two entries.** The
double-entry invariant (entries summing to zero) has no exception for money leaving the
system entirely — the second entry is credited to an internal clearing/settlement
account rather than another customer's `accountId`. Not yet modeled as a real
`accountId` value in code — flagged here as a known future requirement, not implemented
in this scaffold.

---

## Inter-service communication

### `AccountClient` — Feign client to `account-service`

```java
@FeignClient(name = "account-service", url = "${account-service.url}")
public interface AccountClient {
    @GetMapping("/api/v1/accounts/{id}")
    AccountResponse getAccount(@PathVariable("id") UUID id);
}
```

Chosen over `WebClient` because the entire call chain here is already blocking —
Spring MVC + JPA/JDBC — so a reactive client would only add complexity
(`Mono` + forced `.block()`) with no actual non-blocking benefit anywhere in the chain.

**DNS resolution note (same pattern as `api-gateway` and `account-service`):**
`application.yml`'s base config points at the DNS name (`http://account-service:8081`),
correct for the eventual containerized target. `application-local.yml` overrides this
to `http://localhost:8081` for now, since `ledger-service` and `account-service` both
currently run directly on the host, not inside a shared Docker network yet.

**Known current gap:** no resilience wrapping (`@CircuitBreaker`/`@Retry`) around this
client yet. An unhandled downstream failure (e.g. `account-service` down) currently
surfaces as a raw, unshaped `500` — confirmed for real in this project via
`UnknownHostException` when the callee container was stopped in testing. This is
Day 22's task, not fixed here.

---

## API

Base path: `/api/v1/test` — **temporary, for verifying the Feign wiring only.**

### `GET /api/v1/test/account/{id}`

Calls `account-service` through the real Feign client and returns whatever it returns.
Exists purely to prove the inter-service call path works end to end. **This endpoint is
removed once the real `POST /payment-intents` endpoint (Day 10) exists** — it is not
part of the service's intended permanent API surface.

---

## Access control and business logic — deliberate current gaps, flagged for later

There is currently **no enforcement of the double-entry invariant** (entries summing to
zero), **no idempotency-key checking logic**, and **no real payment-creation endpoint**.
All three are deliberate, scoped decisions for this stage of the build, not oversights:

- The zero-sum invariant and its test coverage arrive with the domain-model work
  immediately following this scaffold.
- Idempotency-key enforcement (checking `findByIdempotencyKey()` before doing any real
  work) arrives alongside the real `POST /payment-intents` endpoint.
- No `@ControllerAdvice` exists yet — same deferred-until-both-services-are-further-along
  reasoning as `account-service`.
- No authentication on any endpoint yet — arrives once the security/identity work
  begins, centralized at the Gateway, same as every other service.

---

## Running locally

1. Start the database (from repo root):
   ```bash
   docker compose up -d ledger-db
   ```
2. Run the service:
   ```bash
   mvn spring-boot:run
   ```
   The `local` profile activates by default (set inside `application.yml`), so no extra
   flag is needed. Runs on port `8083`.
3. Verify the schema:
   ```bash
   docker exec -it payforge-ledger-db psql -U payforgeledger -d ledgerdb -c "\dt"
   ```

**Build note:** same parent-POM `-parameters` compiler flag dependency as
`account-service` — required for `@PathVariable` resolution, and also (newly relevant
here) for Feign's Jackson-based record deserialization to match JSON keys to
`AccountResponse`'s constructor parameters by name.

---

## Deferred to later stages of the build (deliberate, not missing)

| Concern | Where it actually belongs |
|---|---|
| Double-entry zero-sum invariant enforcement | Immediately following this scaffold — the domain-model session |
| Idempotency-key checking logic | Alongside the real `POST /payment-intents` endpoint |
| Real payment-intent creation endpoint | Same session as idempotency-key logic |
| Transaction isolation level, overdraft protection | The session following idempotency work |
| `@CircuitBreaker`/`@Retry` around the Feign call to `account-service` | Resilience4j integration session |
| Refund/dispute states and transition rules | Sprint 2 |
| Clearing/settlement account modeling for one-sided movements | Not yet scheduled — flagged as a known future requirement |
| PCI DSS scope decision (does this service touch card data?) | Sprint 3 |
| Global exception handling (`@ControllerAdvice`) | Same deferred point as `account-service` — once both services are further along |
| Unit tests | Deliberately deferred until `notification-service` is also scaffolded, then taught as its own dedicated session |