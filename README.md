# cloud-itonami-isic-1920

Open Business Blueprint for **ISIC Rev.5 1920**: Petroleum Refining
-- batch intake, per-jurisdiction refinery process-safety / major-
accident-hazard regulatory assessment (assay verification), unit
processing, and product yield for a community refiner.

This repository publishes a petroleum-refining actor -- batch intake,
per-jurisdiction refinery-safety regulatory assessment, unit process
and product yield -- as an OSS business that any qualified operator
can fork, deploy, run, improve and sell, so a regional refiner never
surrenders process-safety and yield-accounting data to a closed DCS /
refinery-AI SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **RefiningAdvisor ⊣
Refinery Safety Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:refinery-safety-governor`,
is a UNIQUE keyword fleet-wide (grep-verified: no other blueprint
declares it) -- a fresh, independent build.

**Unlike `cloud-itonami-isic-4920` (which wraps a pre-existing
bespoke capability library `kotoba-lang/logistics`), this vertical is
SELF-CONTAINED**: there is no `kotoba-lang/refining` to delegate
refinery-safety validation to, so the unit-temperature / unit-pressure
/ yield-rate range checks live as pure functions in `refining.registry`
and are re-verified independently by the governor, rather than wrapping
an external capability library's own validated function.

> **Why an actor layer at all?** An LLM is great at drafting a batch
> summary, normalizing records, and reading a thermocouple -- but it
> has **no notion of which jurisdiction's refinery process-safety /
> major-accident-hazard law is official, no license to charge and run a
> real refinery unit or yield real refined product, and no way to know
> on its own whether the measured unit temperature actually lies inside
> the declared safe window, whether the measured unit pressure actually
> lies inside the declared safe window, or whether the realized yield
> fraction actually meets its required fraction**. Letting it process a
> batch or yield product directly invites fabricated regulatory
> citations, a unit running outside its temperature/pressure envelope
> with no flare (overpressure-relief path), and a yield finalized below
> its required fraction -- exposing the crew to a lethal explosion and
> the operator to real liability, for whoever runs it. This project
> seals the RefiningAdvisor into a single node and wraps it with an
> independent **Refinery Safety Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers batch intake through refinery process-safety /
mechanical-integrity regulatory assessment (assay verification), unit
process and product yield. It does **not**, by itself, hold any
operating licence or operating authority required to run a refining
business in a given jurisdiction, and it does not claim to. It also
does not perform the actual physical unit changeover / catalyst
handling itself, or judge refinery economics -- yield-blending and
margin optimization (the blueprint's own `:optimization` technology)
is a follow-up slice, not in this R0. Whoever deploys and operates a
live instance (a qualified shift superintendent / refinery operator)
supplies any jurisdiction-specific operating authority, the real
refinery-unit-robot dispatch integration and the real DCS / refinery-
information-system integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited
execution scaffold so that operator does not have to build the
compliance layer from scratch.

### Actuation

**Charging and running a real refinery unit and yielding real refined
product are never autonomous, at any phase, by construction.** Two
independent layers enforce this (`refining.governor`'s `:unit/process`/
`:product/yield` high-stakes gate and `refining.phase`'s phase table,
which never puts either op in any phase's `:auto` set) -- see
`refining.phase`'s docstring and `test/refining/phase_test.clj`'s
`unit-process-never-auto-at-any-phase`/`product-yield-never-auto-at-
any-phase`. The actor may draft, check and recommend; a human shift
superintendent is always the one who actually charges a unit or
finalizes a yield. Grounded in process-safety doctrine (the same
discipline every regulator in `refining.facts` codifies, hard-won
after Texas City 2005 and Buncefield 2005: a real process and a real
yield are human sign-off acts) -- a genuine DUAL-actuation shape,
applied SEQUENTIALLY to the SAME batch (process first, yield later),
unlike `retailops`/4711's own `:kind`-distinguished alternative-action
shape.

## The core contract

```
batch intake + jurisdiction facts (refining.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ RefiningAdvisor       │ ─────────────▶ │ Refinery Safety Gov.   │  (independent system)
   │ (sealed)              │  + citations    │ spec-basis · evidence- │
   └───────────────────────┘                 │ incomplete · unit-temp │
          │                 commit ◀┼ out-of-range (NEW, two-sided  │
          │                         │ tolerance) · unit-pressure-   │
    record + ledger        escalate ┼ out-of-range (NEW, two-sided  │
          │              (ALWAYS for│ tolerance) · yield-rate-      │
          │       :unit/process/    │ insufficient (NEW) ·          │
          │       :product/yield)   │ contamination-flag-unresolved │
          │                         │ · flare-system-inoperational  │
          ▼                          │ (NEW) · already-processed ·   │
      human approval                 │ already-yielded               │
                                      └───────────────────────┘
```

**The RefiningAdvisor never processes a batch or yields product the
Refinery Safety Governor would reject, and never does so without a
human sign-off.** Hard violations (fabricated regulatory requirements;
unsupported evidence; a unit temperature outside the safe window; a
unit pressure outside the safe window; a yield rate below the required
fraction; an unresolved contamination flag; an inoperational flare
(overpressure-relief path unavailable); a double process/yield) force
**hold** and *cannot* be approved past; a clean process/yield proposal
still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean process + yield lifecycle, plus eight HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an autonomous refinery-unit
robot performs the physical actuation of a distillation/reformer unit
(valve / heat input) under the actor, gated by the independent
**Refinery Safety Governor**. The governor never dispatches hardware
itself: a process-clearing action must have cleared the same sign-off a
human shift superintendent would need. This restates the fleet-wide
robotics premise three ways (ADR-2607011000): the blueprint declares
`:robotics true`, the README names the robot that performs the physical
act, and the Refinery Safety Governor is the independent gate that
robot's command must pass -- a robot may turn the valve / set the heat
input, but only after the governor and a human superintendent both
agree it is safe to.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Refinery Safety Governor, process/yield draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`1920`). Unlike the freight sibling, this vertical is NOT backed by a
separate bespoke domain capability lib: the refinery-safety range
checks (unit-temperature window, unit-pressure window, yield-rate vs
required fraction) are self-contained pure functions in
`refining.registry`, on top of the generic robotics/identity/forms/
dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/refining/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + process AND yield history (dual history). The double-actuation guard checks dedicated `:processed?`/`:yield-finalized?` booleans rather than a `:status` value |
| `src/refining/registry.cljc` | Process/yield draft records, plus the self-contained refinery-safety range-check pure functions (`unit-temp-out-of-range?`, `unit-pressure-out-of-range?`, `yield-rate-insufficient?`) the governor re-verifies against -- no external capability library to delegate to |
| `src/refining/facts.cljc` | Per-jurisdiction refinery process-safety / major-accident-hazard catalog with an official spec-basis citation, honest coverage reporting |
| `src/refining/refiningadvisor.cljc` | **RefiningAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assay-verification/process/yield proposals |
| `src/refining/governor.cljc` | **Refinery Safety Governor** -- 7 HARD checks (spec-basis · evidence-incomplete · unit-temp-out-of-range, the aerospace two-sided-tolerance discipline · unit-pressure-out-of-range, the aerospace two-sided-tolerance discipline · yield-rate-insufficient, the fabrication ratio discipline · contamination-flag-unresolved · flare-system-inoperational) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/refining/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (process/yield always human; batch intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/refining/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/refining/sim.cljc` | demo driver |
| `test/refining/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers batch intake through refinery process-safety /
mechanical-integrity regulatory assessment (assay verification), unit
process and product yield -- the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Batch intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:batch/intake`/`:assay/verify`) | Real DCS / refinery-unit-robot integration, yield-blending and margin optimization |
| Unit process, HARD-gated on full evidence, an in-window unit temperature, an in-window unit pressure, an operational flare (overpressure-relief path), no open contamination flag, plus a double-process guard (`:unit/process`) | |
| Product yield, HARD-gated on full evidence, a sufficient yield rate, and no double-yield (`:product/yield`) | |
| Immutable audit ledger for every intake/assay/process/yield decision | |

Extending coverage is additive: add the next gate (e.g. a sulphur-recovery
compliance check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship ops already establish.

## Jurisdiction coverage (honest)

`refining.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `refining.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, NOR) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `refining.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `RefiningAdvisor` + `Refinery Safety Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, following the SAME governed-actor
architecture as the other prior actors across this fleet, with its own
distinct, independently-named governor and its own self-contained
refinery-safety range checks. See `docs/adr/0001-architecture.md` for
the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
