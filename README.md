# Product Requirements Document
## H.E.A.R.T. + R.I.S.E. Protocol Suite
### With ARTIE Reference Client Implementation

**Version:** 0.1 — Working Draft  
**Status:** Pre-implementation  
**Author:** Joshua Gough
**Date:** June 2026

---

## 1. Executive Summary

This document defines the requirements for the H.E.A.R.T. (Hypermedia Enforced Agentic Reliable Transactions) and R.I.S.E. (Reciprocal Interface for State Exchange) protocol suite, together with ARTIE (Autonomous Restful Traversal and Interactive Engine), the reference client implementation.

The suite addresses a fundamental architectural failure in the current agentic AI landscape: the use of large language models as deterministic routers over tightly coupled, out-of-band schema definitions. This approach produces systems that are expensive to operate, brittle under server evolution, and prone to hallucination. H.E.A.R.T. and R.I.S.E. correct this by applying Roy Fielding's REST constraints — specifically the uniform interface and hypermedia as the engine of application state — to agent-to-system and system-to-system integration.

The result is a protocol suite that reduces token cost, eliminates a primary class of agent hallucination, and enables server-side evolution without client redeployment.

---

## 2. Problem Statement

### 2.1 The Current State

Agentic AI systems in 2026 are overwhelmingly built on one of two patterns:

**Pattern A — OpenAPI tool calling.** The agent is provided with an API schema before any interaction with the server. The schema lives in the system prompt or in a tool definition file. The agent constructs requests from this prior knowledge. When the server evolves the schema must be updated, the agent redeployed, and the context window reloaded. Token cost is high and fixed regardless of what the agent actually needs to do.

**Pattern B — MCP tool manifests.** A marginal improvement that makes tool definitions machine-readable but retains the fundamental coupling. Tool definitions are still out-of-band. The agent still knows what it can do before it asks the server. The server still cannot evolve without breaking the client.

Both patterns share a root cause: the client carries knowledge of the server's interface rather than discovering it at runtime from the server's responses. This is what Roy Fielding identified in 2008 as the defining failure of so-called REST APIs. It remains unaddressed in the agentic context.

### 2.2 The Consequences

**Token cost.** A 50,000-token OpenAPI spec loaded into every agent context window at every session is a fixed tax. Multiply by request volume and the cost is significant. The spec is loaded whether the agent uses ten endpoints or one.

**Hallucination.** When the agent constructs requests from prior schema knowledge rather than server-provided affordances, it operates on potentially stale information. Field names change. Endpoints move. Business rules evolve. The agent does not know. It guesses. It hallucinates plausible-but-invalid requests.

**Brittleness.** Server evolution requires client redeployment. This creates coordination overhead, versioning complexity, and the inevitable breaking changes that consume engineering time and create instability.

**Context rot.** Conversation history accumulates across turns. The context window fills with stale state. Reasoning quality degrades. Cost increases. The agent is carrying the weight of every previous turn while trying to reason about the current one.

### 2.3 The Root Cause

Fielding named it in 2008: if the engine of application state is not being driven by hypertext then it cannot be RESTful. The industry built APIs that are not RESTful in this sense. It then built agents on top of those APIs and compounded the problem.

The root cause is the absence of a media type that carries both resource state and available transitions simultaneously, with embedded schema constraints, discoverable at runtime from the server's response alone.

---

## 3. Vision

A generic agent — one carrying no domain-specific knowledge — enters a system with a single URI and a known media type. The server responds with the current state of the world and the complete map of what is permitted right now. The agent reasons over that map, maps user intent to available affordances, and executes a transaction constrained entirely by the server-provided schema. The context window remains flat. The token cost is predictable and minimal. The server can evolve freely. The agent adapts at the next sync cycle.

The same agent, without code changes, can interact with a garden management system, a basketball scoring system, an insurance claims system, or any other domain — because the agent is coupled to the media type not to the domain.

This is the web's original design applied to the agentic era. It is what Fielding described and what the industry failed to build for twenty years.

---

## 4. Goals

- Define a media type that carries resource state and affordances as an inseparable unit
- Define compliant server behaviour that enforces business rules through affordance presence and absence
- Define compliant client behaviour that enters with a bookmark and discovers everything at runtime
- Define a hash-based registry that eliminates redundant schema fetches
- Define the Sync-Reason-Act cycle as the agent's processing model
- Define the RISE envelope pattern for asynchronous reciprocal interaction
- Define a profile negotiation mechanism that reduces response weight for warm clients
- Produce a reference implementation — ARTIE — that demonstrates all of the above across two unrelated domains
- Demonstrate measurable token cost reduction versus OpenAPI tool calling baseline
- Demonstrate zero hallucinated API calls under the H.E.A.R.T. constraint

---

## 5. Non-Goals

- Retrofitting existing non-compliant APIs
- Replacing AsyncAPI, OpenAPI, or gRPC in their existing use cases
- Defining a general purpose agent framework
- Solving hallucination in the semantic reasoning step — H.E.A.R.T. constrains structural hallucination only
- Prescribing server implementation technology

---

## 6. Protocol Requirements

### 6.1 Media Type

The H.E.A.R.T. media type SHALL be defined in three serialisation profiles:

- `application/heart+json` — machine to machine, default
- `application/heart+yaml` — LLM mediated, human readable
- `application/heart+markdown` — human rendering, documentation

All three profiles SHALL carry identical semantic content. The serialisation format SHALL NOT affect protocol compliance.

A conformant H.E.A.R.T. response SHALL contain:

**Affordance Form.** A discrete unit representing a single available transition. SHALL contain:
- `rel` — semantic relation name identifying the affordance
- `href` — target URI provided by the server
- `hash` — fingerprint of the current schema for this affordance

**Field Schema.** Embedded within each affordance form. SHALL contain:
- `fields` — map of field name to declared type
- `required` — list of required field names

**Affordance Manifest.** A collection of affordance forms representing all currently available transitions.

**Resource State.** Domain data representing the current state of the resource.

**Atomic Unit.** Resource state and affordance manifest SHALL be delivered simultaneously in a single response. Delivering them separately is non-compliant.

### 6.2 Compliant Server Behaviour

A H.E.A.R.T. compliant server SHALL:

- Serve resource state and affordance manifest simultaneously in every response
- Vary the affordance manifest based on current domain state
- Assign a stable hash fingerprint to every affordance schema
- Reject transitions not present in the current manifest using standard HTTP response semantics
- Respond to the `Accept-Profile` header
- Serve hash fingerprints only when `detail=hash` is declared in the profile
- Serve full embedded schemas when no profile is declared
- Advertise supported profiles via the `Link` header

Business rules SHALL be enforced through affordance presence and absence. An action not present in the current manifest CANNOT be exercised. This is the primary mechanism for mechanical reliability.

### 6.3 Compliant Client Behaviour

A H.E.A.R.T. compliant client SHALL:

- Enter any interaction with a single bookmark URI and no prior domain knowledge
- Declare the H.E.A.R.T. profile URI on every resource request
- Declare `detail=hash` when the registry contains schemas for known affordances
- Omit `detail=hash` on cold start to receive full embedded schemas
- Compare received fingerprints against the local affordance registry
- Fetch only schemas whose fingerprint is absent from or changed in the registry
- Reason only over affordances present in the current manifest
- Populate form fields within the constraints declared by the affordance schema
- Never construct a URI from prior knowledge

### 6.4 Profile Negotiation

Four profile levels SHALL be defined:

**Functional Profile** — declares the client understands H.E.A.R.T. affordance manifests. Minimum requirement for compliance.

**Data Profile** — declares the client is familiar with a specific domain vocabulary. Enables the server to omit explanatory metadata for known rel names.

**Detail Profile** — declares the client's registry state. The `detail=hash` parameter instructs the server to return fingerprints only, suppressing full schema bodies from the manifest.

**Behavioural Profile** — declares the agent's operational mode. `interactive` indicates a human is in the loop and clarification can be sought. `autonomous` indicates deterministic affordances only are appropriate.

Graceful degradation SHALL be supported. A request without `Accept-Profile` SHALL receive a full response. A `406 Not Acceptable` SHALL be returned only when the declared profile cannot be honoured.

### 6.5 Affordance Registry

The affordance registry is a client-side cache of schema definitions keyed by hash fingerprint.

The registry SHALL:

- Start empty — no schemas are assumed known before first interaction
- Store a schema keyed by its fingerprint
- Return null on miss without error
- Support lightweight presence check without full retrieval
- Be idempotent on repeated store of the same fingerprint
- Persist across sessions to maximise caching benefit

### 6.6 Sync Behaviour

The sync step is the first step of every Sync-Reason-Act cycle.

The sync step SHALL:

- Compare all fingerprints in the received manifest against the registry
- Identify dirty affordances — those whose fingerprint is absent or changed
- Fetch full schemas for all dirty affordances before reasoning begins
- Complete with zero server requests when all fingerprints are known
- Complete with zero server requests on subsequent sessions when the registry is warm and the server manifest is unchanged

### 6.7 Sync-Reason-Act Cycle

The agent's processing model SHALL follow three steps in sequence:

**Sync.** Fetch the current resource state and manifest. Resolve all dirty affordance schemas. The agent SHALL NOT reason until the manifest is fully resolved.

**Reason.** Map user intent to available affordances using the rel names as semantic anchors. The LLM SHALL operate only on affordances present in the current manifest. When intent is ambiguous or no affordance matches the agent SHALL seek clarification from the user rather than guess.

**Act.** Populate the selected affordance form within the declared schema constraints. Submit to the server-provided href. Receive the next resource state and manifest. Restart the cycle.

The context window SHALL contain only the current resource state and manifest. Conversation history SHALL be managed to prevent context rot. The token cost SHALL be flat and predictable across cycles.

---

## 7. R.I.S.E. Requirements

R.I.S.E. is a specialisation of H.E.A.R.T. for asynchronous reciprocal interaction. It is not a separate protocol. It extends the H.E.A.R.T. media type with a standard vocabulary of async rel names.

### 7.1 Core Principle

In a RISE interaction, client and server are roles not identities. The originator begins as client. The recipient begins as server. When the recipient's work is complete the roles reverse. The recipient becomes client. The originator becomes server. The uniform interface applies in both directions.

### 7.2 The RISE Envelope

The RISE envelope is a media type construct carried in the initial request. It is the self-addressed stamped envelope.

The envelope SHALL contain:

- `reply-to` — URI at which the originator will receive the callback
- `template-rel` — semantic relation name of the expected response
- `template-hash` — fingerprint of the template schema
- `template-fields` — list of field names the originator understands

The envelope SHALL embed sufficient information for the recipient to construct a valid callback without any out-of-band schema knowledge. The template hash enables the recipient to check its registry before fetching.

### 7.3 RISE Request Behaviour

A RISE compliant originator SHALL:

- Construct an envelope containing reply-to, template rel, template hash, and declared fields
- Attach the envelope to the outbound request
- Not block on the response
- Declare the H.E.A.R.T. profile on the envelope

### 7.4 RISE Recipient Behaviour

A RISE compliant recipient SHALL:

- Acknowledge receipt immediately with 202 Accepted
- Perform work asynchronously
- Check the affordance registry for the envelope's template hash
- Fetch the template schema only on registry miss
- Map its domain state onto the declared template fields using rel names as semantic anchors
- Submit the callback to the reply-to URI
- Never expose its internal domain model in the callback

### 7.5 RISE Rel Name Vocabulary

The core RISE vocabulary SHALL be minimal. The following rel names are defined:

- `reply-to` — success callback shape
- `error-reply` — failure callback shape
- `progress` — optional intermediate status update

Domain-specific rel names SHALL be defined in domain profiles layered on top of the core vocabulary. Domain rel names SHALL NOT be added to the core vocabulary.

### 7.6 RISE Fallback Behaviour

A RISE compliant originator SHALL:

- Define a non-response window after which fallback is triggered
- Select a fallback recipient from runtime-discovered agents in the affordance registry
- Reuse the original envelope unchanged for the fallback request
- Accept a callback from the fallback recipient in the same declared template shape
- Record fallback provenance in the result

### 7.7 Shared Registry

The affordance registry SHALL be shared between H.E.A.R.T. and RISE interactions. A schema cached during a H.E.A.R.T. sync step SHALL be available to RISE envelope processing without re-fetch. A template hash stored by the originator SHALL be discoverable by the recipient if both participants share a registry instance or if the hash has been previously encountered by the recipient.

---

## 8. ARTIE — Reference Client

ARTIE is the reference implementation of a H.E.A.R.T. and RISE compliant client. ARTIE demonstrates that a single generic agent, carrying no domain-specific knowledge, can interact correctly with any H.E.A.R.T. compliant server.

### 8.1 ARTIE Requirements

ARTIE SHALL:

- Enter any H.E.A.R.T. compliant server with a single URI and no prior domain knowledge
- Execute the Sync-Reason-Act cycle correctly
- Maintain a persistent affordance registry across sessions
- Declare the appropriate profile on every request
- Operate in interactive mode — seeking clarification from the user on ambiguous intent
- Support RISE envelope construction and callback reception
- Demonstrate correct fallback behaviour on recipient timeout
- Produce measurable token cost comparison versus OpenAPI baseline

### 8.2 Reference Domains

ARTIE SHALL be demonstrated against two unrelated domains to prove domain agnosticism:

**Domain A — Agricultural management.** A garden bed cell state machine with affordances that vary by plant lifecycle state. Demonstrates synchronous H.E.A.R.T. interaction and RISE async counting via remote vision agents.

**Domain B — Sports scoring.** A real-time game state machine with affordances that vary by game phase. Demonstrates that the same ARTIE instance, without modification, navigates a completely unrelated domain correctly.

The two domains are chosen for maximum contrast. Their only shared characteristic is H.E.A.R.T. compliance. ARTIE's ability to interact correctly with both from a single bookmark demonstrates the generic interface working as designed.

### 8.3 Simulation Requirements

All protocol behaviour SHALL be demonstrable via in-memory simulation with no external dependencies:

- Simulated server with scripted state machine
- Scripted intent resolution replacing live LLM calls
- Simulated async recipients with configurable timeout behaviour
- Full cycle verification producing correct state transitions
- Registry cache hit and miss verification
- Warm registry lean request verification
- Cold start full schema verification
- Fallback path completion verification

---

## 9. Success Metrics

- Token cost per transaction: target 90% reduction versus OpenAPI tool calling baseline
- Hallucinated API calls: zero under H.E.A.R.T. constraint
- Server evolution adaptability: agent adapts to manifest changes within one sync cycle with zero client code changes
- Registry efficiency: zero schema fetches on second cycle against unchanged server
- Domain agnosticism: ARTIE interacts correctly with both reference domains from bookmark entry with no domain-specific configuration

---

## 10. Out of Scope for V1

- Registry persistence implementation details
- LLM provider selection
- Server implementation framework
- Transport layer below HTTP
- Authentication and authorisation mechanisms
- Rate limiting and throttling
- Multi-agent coordination beyond two-party RISE

---

## 11. Open Questions

- Registry persistence strategy across sessions and devices
- Standardisation path for H.E.A.R.T. media type registration with IANA
- Relationship to W3C Content Negotiation by Profile working draft
- Formal definition of rel name vocabulary governance
- Versioning strategy for the core media type

---

## 12. Architectural Alignment

This protocol suite is explicitly grounded in Roy Fielding's REST constraints as stated in his 2000 dissertation and his 2008 blog post REST APIs Must Be Hypertext-Driven. Every requirement in this document traces to one or more of Fielding's six principles:

- Protocol independence
- No private protocol extensions
- Media types as the descriptive effort
- No fixed resource names or hierarchies
- No implementation details exposed to clients
- Entry with bookmark and known media types only

H.E.A.R.T. and RISE are not an interpretation of these principles. They are a direct implementation of them in the context of agentic AI systems.

