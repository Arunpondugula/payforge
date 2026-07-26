# ADR-001: Service Discovery Strategy

**Status:** Accepted
**Date:** 2026-07-26
**Author:** Arun Pondugula
**Sprint:** Sprint 1 — Microservices Foundation & Ledger Core

## Context

PayForge is composed of independently deployable services (api-gateway,
account-service, ledger-service, notification-service — joining in Sprint 2)
that must call one another over the network. Service instances are not
static: container restarts, redeploys, and horizontal scaling all change
the underlying IP address an instance is reachable at. Every inter-service
call therefore requires a way to resolve a logical service name to a
currently-live address, without hardcoding IPs.

Historically, systems built on Spring Cloud solved this with a dedicated
service registry — Netflix Eureka — paired with client-side load balancing
(Ribbon). Eureka requires every service to embed a client library, register
itself on startup, and heartbeat periodically to stay marked alive; callers
poll the registry, cache it locally, and pick an instance themselves.

PayForge's deployment targets (Docker Compose locally, Kubernetes via
Minikube from Sprint 4 onward) both include native service discovery as a
platform capability — a private DNS zone where every declared service gets
a stable, resolvable name, with instance health and routing managed by the
platform itself.

## Decision

PayForge services will call each other by **plain service name over
standard DNS resolution** — no dedicated service registry (e.g. Eureka)
will be introduced.

- **Locally (Docker Compose):** services resolve each other via Compose's
  embedded DNS, using the service name defined in `docker-compose.yml`
  (e.g. `http://account-service:8080`).
- **In-cluster (Kubernetes, from Sprint 4):** services resolve each other
  via Kubernetes Service DNS (CoreDNS), using the Service's cluster-local
  name (e.g. `http://account-service.payforge.svc.cluster.local`, or the
  short form `http://account-service` within the same namespace).

No `discovery-server` module will be built. No Eureka client dependency
will be added to any service. Inter-service HTTP clients (Feign/WebClient,
starting Day 7) will be configured to call these DNS names directly.

## Rationale

1. **The platform already solves both halves of discovery.** Registration
   (an instance announcing itself as alive) happens as a side effect of
   normal Pod/container lifecycle management — kubelet and the Endpoints
   controller in Kubernetes, Compose's own container tracking locally.
   Lookup happens via standard DNS resolution, which every language and
   HTTP client already supports natively — no special client library is
   required in any service.

2. **A dedicated registry would be redundant infrastructure.** Running
   Eureka would mean deploying, monitoring, and keeping highly available a
   service whose entire job — tracking "what's alive and where" — the
   platform already performs as a core function. This is the reason Eureka
   is now in Spring Cloud maintenance mode: it was built for a pre-container-
   orchestration world.

3. **Lower operational surface area for a small team.** With no registry
   to run and no client library to configure per service, there is one
   fewer category of production incident (registry unavailability, stale
   heartbeat state, self-preservation-mode false positives) to reason about.

## Consequences

**What we gain:**
- Zero additional infrastructure to deploy or operate for discovery.
- No per-service dependency or configuration for a discovery client.
- Near-zero staleness: a Kubernetes Service's ClusterIP is stable for the
  Service's lifetime, and Endpoints updates near-instantly as Pods change,
  unlike Eureka's ~30s client poll/cache window.

**What we give up / trade-offs to note explicitly:**
- **Load balancing moves from the application layer to the platform
  layer.** Under Eureka+Ribbon, the *calling service* chose which instance
  to hit (client-side load balancing). Under DNS-based discovery, the
  platform (Compose's embedded proxy locally, `kube-proxy` in Kubernetes)
  performs this routing transparently — the caller has no visibility into,
  or control over, which specific instance handled a given request. This is
  a deliberate trade: acceptable for PayForge's scale, and consistent with
  current industry practice for platform-native deployments.
- **No cross-cluster or cross-environment discovery.** DNS-based discovery
  as configured here is scoped to a single Compose network or a single
  Kubernetes cluster. If PayForge ever needed services split across
  multiple clusters or clouds, this would require additional tooling (e.g.
  a service mesh) — out of scope for this project.
- **Dependent on the underlying platform's DNS being correctly configured.**
  A misconfigured Service/Endpoint object or DNS policy becomes the
  equivalent failure mode that a misconfigured Eureka registration would
  have been. This risk is accepted as standard platform-operations
  responsibility, not unique to this decision.

## Alternatives Considered

| Option | Rejected because |
|---|---|
| Netflix Eureka + Ribbon | Requires operating a dedicated registry and embedding a client library per service, solving a problem the deployment platform already solves natively. Also in Spring Cloud maintenance mode. |
| HashiCorp Consul | Same category as Eureka — a capable general-purpose registry, but still redundant additional infrastructure when the target platform (Kubernetes) has this built in. |
| Static/hardcoded IP config | Does not tolerate instance restarts, redeploys, or scaling — breaks almost immediately in any dynamic deployment. |