# PayForge

A microservices-based payment processing system built as a hands-on learning project (FDE readiness prep).

## Modules

| Module | Responsibility |
|---|---|
| `common` | Shared DTOs, custom exceptions, utilities used across all services |
| `api-gateway` | Single entry point — routing, (later) auth and rate limiting |
| `account-service` | Owns identity and account/balance data |
| `ledger-service` | Owns the double-entry ledger, transactions, and payment intents |
| `notification-service` | Owns audit logging and webhook delivery |

## Build

This is a Maven multi-module (reactor) build — one repo, five independently buildable/deployable modules.

## mvn clean install

This builds all 5 modules in dependency order (`common` first, since the 4 services depend on it).

## Stack

- Java 21
- Spring Boot 3.5.16
- Spring Cloud 2025.0.3 (needed starting Day 6 for api-gateway)

## Documentation

- [Project structure overview](docs/PayForge-Project-Structure.pdf)
- [Architecture diagram](docs/payforge_architecture.png)

## Status

Day 4 — parent project scaffolded. Real service logic begins Sprint 1 (Day 5).