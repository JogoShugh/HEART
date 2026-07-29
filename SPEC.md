# H.E.A.R.T. + R.I.S.E. Protocol Suite — v0.3

## 0. Status of this document

This supersedes v0.2. Where the two disagree, this document is
authoritative. Section 0.1 summarizes what changed and why — read it
before assuming any behavior from a v0.2-era implementation still
holds.

v0.2 specified a **fat-prompt** client: nearly everything — sync
mechanics, hash comparison, HTTP handling, RISE envelope plumbing — ran
as LLM-mediated reasoning, with the explicit stated intent that this
was scaffolding, not the target architecture. v0.3 is the client that
scaffolding was building toward: **inference earns its way into exactly
one step.**

### 0.1 Changelog from v0.2

- **Architecture inverted from top-down to bottom-up.** v0.2 started
  with an LLM doing everything and described a future where code would
  displace it. v0.3 starts from deterministic code and requires
  inference to justify its presence at each remaining step. Only one
  step survives that test — see §3.
- **Three-layer content-addressed resource model formalized** (§2).
  v0.2 had no caching model beyond ordinary HTTP; hash comparison was
  described as something the LLM does by string equality, with no
  layer structure underneath it.
- **Two dialects, formally specified, including a real, previously
  wrong shape.** v0.2 assumed one schema-bearing format. v0.3 specifies
  both HAL-FORMS (Amundsen, `_templates`) and hal-schema-forms (jbadeau,
  `_forms`) as first-class, negotiated dialects — see §4. The
  hal-schema-forms shape in earlier working drafts of this project
  incorrectly used `_templates` for both dialects; confirmed against
  jbadeau's own published shape, the correct top-level key is `_forms`,
  with `_links.target.href` nesting and a `schema` object carrying a
  real `$id`.
- **URI Templates required for affordance-set targets** (§2.3). An
  earlier draft resolved a specific bed ID into a set's `target.href`,
  which silently defeated cross-resource cache sharing — two resources
  in the same state produced different bytes and different hashes.
  Targets in a shared affordance-set MUST remain literal, unexpanded
  templates (`/beds/{bedId}/actions/water-cell`, `templated: true`).
- **`Prefer: embed=schemas` formally added** (§5), per RFC 7240,
  as this project's own registered extension token, alongside the
  RFC's own `return=minimal`/`return=representation`.
- **Event sourcing specified as the recommended, not mandated, backend
  pattern** (§8). Necessary for correct point-in-time reconstruction;
  optional for implementations that don't need history.
- **§9 (multi-remote guidance) is new.** v0.2 assumed a single server.
- **§10 (orchestration compression) is new.** Clarifies that bulk
  affordances and client-side batch planning are the specified
  mechanisms for N-target operations — no sandboxed code execution
  required or recommended.
- **Ambient affordances named as the governing concept** (§1.2).

---

## 1. Core principles

### 1.1 Domain-agnostic by construction

The client carries no domain-specific knowledge of any server it
talks to — no field names, endpoint paths, business rules, or valid
values known in advance. Everything the client knows about what's
currently possible comes from the most recent response. This is
unchanged from v0.2 and remains the foundational constraint.

### 1.2 Ambient affordances

The governing concept for this architecture, borrowed and specialized
from Ned Letcher's usage (Thoughtworks): *structural properties of the
environment itself that make it legible, navigable, and tractable to
an agent operating within it.* In this protocol, the specific
instantiation is: **the set of currently-legal actions is never
searched for, filtered, or retrieved from a larger catalog — it is
what the environment ambiently produces, given its current state,
with nothing more and nothing less ever present to reason over.**

This is the precise property that distinguishes this design from
tool-search or dynamic-discovery patterns layered onto a static
catalog (MCP Tool Search, Code Mode's `search` step, etc.): those
narrow a fixed haystack faster. This protocol never has a haystack —
only what's ambiently true right now.

### 1.3 Bottom-up, not top-down

Build deterministic code first. Require each remaining unit of
inference to justify its own presence. In this protocol exactly one
step survives that requirement — see §3 (Reason). Sync, caching,
validation, and HTTP mechanics are ordinary code, not LLM-mediated
reasoning, regardless of how they were prototyped.

---

## 2. The three-layer resource model

Every domain resource is modeled across three layers, separated by
**how often each layer's content actually changes.**

### 2.1 Layer 1 — Resource (mutable)

Plain HTTP resource. Standard conditional caching applies: server
issues `ETag`, client sends `If-None-Match`, server replies `304 Not
Modified` when unchanged. This is the only layer where the same URL
legitimately returns different bytes over time.

Concurrency: writes MAY require `If-Match` against the current `ETag`
(or an equivalent sequence token — see §8.2). A mismatch returns `412
Precondition Failed`.

### 2.2 Layer 2 — Affordance-set (immutable)

The set of actions currently legal, given the resource's current
state. Named by a hash of its own canonical content, not by the
resource pointing at it.

```
GET /beds/2
→ { "status": "READY_TO_HARVEST",
    "_links": {
      "<rel>": {
        "href": "/affordance-sets/sha256-<hash>",
        "type": "<negotiated dialect's media type>"
      } } }
```

Because the URL is a hash of the content, `Cache-Control: public,
max-age=31536000, immutable` is a factual statement. Any resource
reaching the identical state resolves to the identical URL — this is
the core cross-resource caching guarantee this protocol exists to
provide, and it MUST hold for any conforming implementation.

**Hash computation MUST exclude anything that resolves per-request.**
Specifically, target hrefs inside an affordance-set MUST remain
literal, unexpanded URI Templates (RFC 6570) — see §2.3. An
implementation that resolves a specific resource ID into the set
before hashing breaks the cross-resource sharing guarantee and is
non-conforming.

### 2.3 Layer 3 — Schema (immutable)

Field definitions for one specific action. Same principle, named by a
hash of its own bytes, independently cacheable and independently
shareable across every affordance-set that happens to reference an
identical set of fields.

### 2.4 URI Templates for shareable targets

Within a layer-2 document, every action target MUST be expressed as
an unresolved URI Template with `templated: true`:

```json
"target": { "href": "/beds/{bedId}/actions/water-cell", "templated": true }
```

The client is responsible for local variable substitution before
issuing the write. This is what makes an affordance-set genuinely
resource-agnostic: identical bytes serve every resource currently in
that state, satisfying real HTTP cache semantics with no query
parameter or resolved-ID variance in the cache key.

A resolved, non-templated href MAY appear only in a response that is
already scoped to one specific resource and not intended for
cross-resource sharing (e.g., the collapsed single-response mode in
§5).

---

## 3. The Sync-Reason-Act cycle

### 3.1 Sync (deterministic)

1. Fetch the current resource (bookmark on first turn; the `href` of
   the last acted-on affordance thereafter).
2. Negotiate dialect via `Accept` (§4).
3. Compare the response's layer-2 pointer against the local cache by
   plain string equality. A hash is opaque — never computed,
   verified semantically, or reasoned about by the model. This
   comparison is code, not inference.
4. Resolve every layer-2 and layer-3 miss via ordinary HTTP fetch,
   in parallel where independent. **Deduplicate by URL before
   fanning out concurrent fetches** — two different affordances
   sharing identical content (and therefore an identical hash) MUST
   NOT race each other on the same in-flight request. This was a
   real, found defect in an early implementation and is now a hard
   requirement, not a suggestion.
5. Do not proceed to Reason with unresolved misses.

### 3.2 Reason — the one step inference is required to earn

1. Given the fully-resolved, currently-legal affordance set — never
   anything wider — map the user's stated intent to exactly one
   `rel`/action name, using its schema as the only signal.
2. If more than one affordance plausibly matches, or none does, ask
   rather than guess (unless the session is explicitly configured for
   autonomous operation, and even then, only within affordances the
   server has chosen to expose under that mode).
3. Populate the chosen affordance's fields strictly within schema
   constraints. Do not fabricate a value the user didn't supply or
   the current resource state didn't derive.
4. Output: a single structured selection (`rel` + field values). This
   is intent classification and slot filling over a schema discovered
   live, not authored ahead of time.

### 3.3 Act (deterministic)

1. Expand any URI Template locally, using only variables the client
   already possesses (e.g. the resource ID from the URL it originally
   synced).
2. Submit to the resolved `href`, with the concurrency token from
   Sync attached per §7.1.
3. Never construct a URI from anything other than an `href` the
   server provided, current or prior.
4. On response, restart the cycle at Sync. Act is never terminal.

---

## 4. Dialect negotiation

Two dialects, negotiated via `Accept`:

```
Accept: application/hal+json, application/prs.hal-forms+json
    → "halforms" dialect (Mike Amundsen's HAL-FORMS)

Accept: application/hal+json;profile="https://github.com/jbadeau/hal-schema-forms"
    → "jsonschema" dialect (jbadeau/hal-schema-forms)
```

No explicit signal → server defaults to `halforms` (documented
default: the more universally implementable choice, since a bare HAL
client already ignores properties it doesn't recognize per HAL's own
extensibility rules).

Because the two dialects produce genuinely different bytes for the
same logical affordance, they legitimately resolve to **different**
layer-2 and layer-3 hashes for the same underlying capability — this
is correct, not a bug, the same way a JPEG and a PNG of the same photo
have different content hashes.

### 4.1 HAL-FORMS dialect (halforms)

Reserved property: `_templates`. Real, inline `properties` per
Amundsen's spec — this is what makes the response render in an
unmodified HAL-FORMS client with zero further requests. If a
collection has exactly one member, its key SHOULD be `"default"` per
the base spec — this is correct, spec-mandated behavior, not
something to work around.

**Known implementation hazard, not a protocol defect:** frameworks
that derive HAL-FORMS `readOnly` from JavaBean-style setter
inspection (confirmed against a real, open Spring HATEOAS issue) will
mark a field read-only if the underlying language's immutable-by-default
value types (e.g. Kotlin `val`) compile without a setter. Request DTOs
MUST expose mutable properties for every field intended to be
writable in this dialect.

### 4.2 hal-schema-forms dialect (jsonschema)

Reserved property: **`_forms`** — not `_templates`. Confirmed directly
against jbadeau's own published shape:

```json
{
  "_links": { "self": { "href": "..." } },
  "_forms": {
    "<name>": {
      "_links": { "target": { "href": "...", "templated": true } },
      "method": "POST",
      "contentType": "application/json",
      "schema": { "$id": "/schemas/sha256-<hash>" }
    }
  }
}
```

Note the structural differences from §4.1: the target href is nested
under `_links.target`, not a flat string; `schema` — not `schemaRef`
— is the reserved field, and it always carries a real `$id`, per the
spec's own allowance that `schema` "MAY contain the referenced schema
or a dynamic subset." Default (no `Prefer`) renders `schema` as
`{"$id": "..."}` alone — a minimal, spec-legal subset. `Prefer:
embed=schemas` (§5) expands it to the full body, with the same `$id`
retained for verification.

---

## 5. `Prefer: embed=schemas`

An RFC 7240 extension token, following the same pattern PostgREST uses
for its own non-standard preferences. Any token beyond the RFC's own
`return=minimal`/`return=representation` is legal per the RFC's
extensibility design; this project registers `embed=schemas` for its
own use.

- Absent → pointer-only rendering at whichever layer is being
  requested (layer-2's `schema.$id` alone, or layer-1's plain pointer
  to layer-2).
- Present → the deepest available detail is inlined. At layer-1, this
  collapses all three layers into one response (the RFC's own
  "save the round trip" case, extended one hop further); at layer-2,
  it expands `schema` to the full body.

Responses honoring this preference MUST include `Preference-Applied:
embed=schemas` and MUST include `Prefer` in `Vary`, alongside `Accept`
— both headers now affect representation, and any cache sitting in
front of this service needs both listed to key correctly.

**Embedding a layer-1 response collapses cross-resource sharing for
that response, by design.** This is the documented trade of "one
round trip" against "shared cache entry" — the same trade a browser
makes with inline `<style>` versus a linked, cacheable stylesheet.
Neither is universally correct; the preference exists so the client
chooses per request.

---

## 6. Hash and content-addressing rules

- A hash is a real, computed function of the referenced content's own
  bytes — never fabricated, never assumed correct without having been
  computed by deterministic code.
- A hash is compared by plain string equality only. It is never
  reasoned about semantically or reconstructed by inference.
- If a cache is ever keyed by hash **alone**, independent of origin
  (permitting cross-origin sharing of byte-identical content — a real
  and legitimate future extension, not required by this version), the
  fetched bytes MUST be re-hashed and verified against the requested
  hash before being trusted. Skipping this verification converts a
  cache into a poisoning vector. The default, required behavior in
  this version is origin-partitioned caching, which needs no such
  verification, exactly as ordinary browser HTTP caching already
  provides for free.

---

## 7. Error and concurrency semantics

| Code | Meaning | Client behavior |
|---|---|---|
| 403 | Business rules currently prohibit this affordance | Surface to user; do not retry with a similar payload |
| 404 | Resource gone | Stop; do not reconstruct a URI |
| 405 | Wrong method | Client bug; surface plainly, do not silently retry with a different method |
| 409 | Business-rule conflict (e.g. illegal state transition) | Distinct from 412 — do not conflate. Re-sync and inform the user of the actual constraint |
| 412 | Concurrency conflict (stale write) | Distinct from 409 — a real "someone else wrote first" race, detected by the server's own concurrency token check. Re-sync from scratch; never resubmit the stale act |
| 422 | Validation failure | Re-check populated fields against the schema just re-fetched; ask the user to correct; never guess a fix |
| 428 | Stale hash | Evict, re-sync, re-fetch. Never keep the stale entry as a fallback |

### 7.1 Concurrency token

A resource's concurrency token (an `ETag`, or an event-sourced
sequence number rendered as one — see §8.2) MUST be attached via
`If-Match` on writes, where the server chooses to require it. A
mismatch is a `412`, distinct in kind from a `409` — the former means
"reality moved since you last looked," the latter means "what you're
asking is not legal from the current state," and a conforming client
MUST NOT collapse these into one handling path.

---

## 8. Recommended backend pattern: event sourcing

Not mandated — a resource backed by simple current-state storage
still satisfies §2–§7 — but recommended wherever historical or
point-in-time queries have any value, since it is the only pattern
that provides them without bolting on a separate audit mechanism
after the fact.

### 8.1 Shape

- **Aggregate root**: bare identity only. No derivable state stored
  directly.
- **Event log**: append-only, one row per state-changing action, each
  carrying a per-aggregate sequence number.
- **Snapshot** (optional): a disposable, periodically-recomputed fold
  result, purely a performance optimization. Deleting it entirely
  must never change correctness, only the cost of the next read.

### 8.2 Concurrency via sequence number

Appending event *N+1* requires knowing the aggregate's current tip is
*N*. A real database uniqueness constraint on `(aggregate_id,
sequence_no)` — not an application-level check-then-write — is what
actually enforces this; the constraint violation on a losing
concurrent write is what becomes the `412` in §7.

### 8.3 Point-in-time reconstruction

Current state is `project(empty, all_events)`. State as of any past
instant is the same fold, applied to `all_events.filter(occurredAt <=
instant)`. This is not a separate feature — it falls directly out of
choosing to store events instead of current state, and any
implementation choosing this pattern gets it for free.

---

## 9. Multi-remote guidance

This protocol, as specified, governs a client's interaction with
**one environment it can discover from within** — a resource whose
current state ambiently produces its own legal next steps. It does
not, by itself, resolve which of several **unrelated** remotes to
engage with in the first place; that is a different epistemic
situation (there is nothing to discover from, prior to engagement).

- **Caching across multiple remotes is safe by default.** Standard
  HTTP caching already partitions by origin; one client instance
  serving many remotes needs no special isolation for cache
  correctness. Per-remote client instances, if used, are a trust or
  blast-radius decision, not a caching requirement.
- **If N remotes share a common, discoverable directory resource**,
  that directory is itself an ordinary resource under this protocol.
  Selecting among its listed remotes is the same Reason step (§3.2)
  applied one level up — no separate mechanism required.
- **If N remotes are genuinely disjoint** — no shared registry, no
  affiliated organization, nothing to recurse into — resolving which
  one to engage is out of scope for this protocol. A capability
  advertisement / agent-discovery layer (e.g. Google's A2A Agent
  Cards) occupies that adjacent, complementary layer; this protocol
  and that layer compose but do not overlap.

---

## 10. Orchestration compression for N-target operations

Applying one action across many resources does not require, and this
protocol does not recommend, generating and sandboxing executable
code at request time. Two specified mechanisms cover this case
entirely within the existing model:

### 10.1 Server-side bulk affordances

A bulk action is simply an affordance whose schema accepts a
collection (`{"cellIds": [2, 5, 9], "milliliters": 500}`). It follows
every rule already specified — discoverable only when legal, content-
addressed like any other schema, validated the same way. Business
rules governing what a valid bulk operation even means belong here,
server-side, where domain logic belongs.

### 10.2 Client-side batch planning

Reason MAY emit a single structured plan spanning multiple targets in
one pass (`{"affordance": "waterCell", "targets": [2, 5, 9], "params":
{...}}`); the deterministic client layer executes the fan-out with no
further Reason invocation per target. This is ordinary slot-filling
with an array-shaped slot — not a new mechanism, and not a
justification for introducing a code-generation or sandboxed-execution
runtime into the client.

---

## 11. Testing discipline

Protocol conformance is verified by an incrementally-grown Gherkin
feature suite with Kotlin step definitions, exercising the actual
client and server code paths — not a separate, parallel
reimplementation of the logic under test. Each defect found through
real execution gets a permanent, named regression scenario before the
fix is considered complete. This suite grows with the protocol; it is
not written once and left static.

---

## 12. Hard prohibitions (carried forward, unchanged)

- Never construct a request URI from anything other than a
  server-provided `href`.
- Never act on an affordance absent from the most recently synced
  manifest.
- Never carry schema, field names, or business-rule assumptions from
  one server or domain into another.
- Never submit a field value the user didn't supply and the current
  state didn't derive.
- Never suppress a 403/404/405/409/412/422/428; always surface it per
  §7.
- Never treat a hash as anything but an opaque token for equality
  comparison, except where §6 explicitly requires re-computing one for
  verification.
